package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** TCL-specific, manual memory-only cleanup. Never invokes file/cache cleanup or force-stop. */
final class TclMemoryCleaner {
    private static final String DESCRIPTOR = "com.tcl.guard.memorymanager.IMemoryManagerInterface";
    private static final String CALLBACK = "com.tcl.guard.memorymanager.IOneKeyCleanCompleteListener";
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Consumer<Boolean> result;
    private volatile boolean busy, closed;
    private boolean registered;
    private volatile int generation;
    private final Runnable timeout = () -> finish(false);

    TclMemoryCleaner(Context context) { this.context = context.getApplicationContext(); }

    boolean available() {
        try {
            ServiceInfo service = context.getPackageManager().getServiceInfo(component(), 0);
            return service.enabled && service.applicationInfo.enabled && service.exported
                    && service.permission == null;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return false; }
    }

    private static ComponentName component() {
        return new ComponentName("com.tcl.guard", "com.tcl.guard.memorymanager.TGuardService");
    }

    void clean(Consumer<Boolean> completed) {
        if (busy || closed) return;
        busy = true;
        generation++;
        result = completed;
        main.postDelayed(timeout, 15000);
        try {
            registered = true;
            if (!context.bindService(new Intent().setComponent(component()), connection, Context.BIND_AUTO_CREATE))
                finish(false);
        } catch (RuntimeException unavailable) { finish(false); }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!busy || closed) return;
            final int request = generation;
            worker.execute(() -> {
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    List<String> exclusions = exclusions();
                    if (!DESCRIPTOR.equals(binder.getInterfaceDescriptor())) throw new SecurityException("Unexpected TCL interface");
                    if (!busy || closed || request != generation) return;
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(callback(request));
                    data.writeStringList(exclusions);
                    if (!binder.transact(2, data, reply, 0)) throw new RemoteException("Unsupported cleanup");
                    reply.readException();
                } catch (Exception unavailable) { main.post(() -> { if (request == generation) finish(false); }); }
                finally { data.recycle(); reply.recycle(); }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { finish(false); }
        @Override public void onNullBinding(ComponentName name) { finish(false); }
        @Override public void onBindingDied(ComponentName name) { finish(false); }
    };

    private Binder callback(int request) { return new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
            if (code != 1) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            data.readLong(); // Vendor estimate, deliberately not presented as a measured RAM gain.
            if (reply != null) reply.writeNoException();
            main.post(() -> { if (request == generation) finish(true); });
            return true;
        }
    }; }

    private List<String> exclusions() throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        if (pm.checkPermission(Manifest.permission.QUERY_ALL_PACKAGES, context.getPackageName()) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Full package visibility required");
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_SERVICES);
        Set<String> names = new HashSet<>();
        for (PackageInfo info : packages) names.add(info.packageName);
        if (!names.contains("android") || !names.contains("com.tcl.guard") || !names.contains(context.getPackageName()))
            throw new SecurityException("Incomplete package inventory");
        List<String> protectedPackages = new ArrayList<>();
        Set<Integer> protectedUids = new HashSet<>();
        for (PackageInfo info : packages) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null) throw new SecurityException("Missing package information");
            boolean vpn = false;
            if (info.services != null) for (ServiceInfo service : info.services)
                if (Manifest.permission.BIND_VPN_SERVICE.equals(service.permission)) vpn = true;
            boolean launchable = pm.getLeanbackLaunchIntentForPackage(info.packageName) != null
                    || pm.getLaunchIntentForPackage(info.packageName) != null;
            boolean system = (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (!MemoryCleanPolicy.eligible(info.packageName, launchable, system, vpn,
                    app.uid < 10000, context.getPackageName())) {
                protectedPackages.add(info.packageName);
                protectedUids.add(app.uid);
            }
        }
        // Shared UIDs must not let closing one package stop a protected sibling.
        for (PackageInfo info : packages)
            if (protectedUids.contains(info.applicationInfo.uid) && !protectedPackages.contains(info.packageName))
                protectedPackages.add(info.packageName);
        return protectedPackages;
    }

    private synchronized void finish(boolean success) {
        if (!busy) return;
        busy = false;
        main.removeCallbacks(timeout);
        if (registered) {
            registered = false;
            try { context.unbindService(connection); } catch (IllegalArgumentException ignored) { }
        }
        Consumer<Boolean> completed = result;
        result = null;
        if (!closed && completed != null) completed.accept(success);
    }

    synchronized void close() {
        closed = true;
        finish(false);
        worker.shutdown();
    }
}
