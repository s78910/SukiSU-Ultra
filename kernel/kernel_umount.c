#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/version.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/mount.h>
#include <linux/namei.h>
#include <linux/nsproxy.h>
#include <linux/path.h>
#include <linux/printk.h>
#include <linux/types.h>

#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#include <linux/susfs_def.h>
#endif // #ifdef CONFIG_KSU_SUSFS

#include "kernel_umount.h"
#include "klog.h" // IWYU pragma: keep
#include "allowlist.h"
#include "selinux/selinux.h"
#include "feature.h"
#include "ksud.h"

#include "sulog.h"

#ifdef CONFIG_KSU_SUSFS
bool susfs_is_boot_completed_triggered = false;
extern u32 susfs_zygote_sid;
extern bool susfs_is_mnt_devname_ksu(struct path *path);
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
extern void susfs_run_sus_path_loop(uid_t uid);
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_PATH
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
extern bool susfs_is_log_enabled __read_mostly;
#endif // #ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
static bool susfs_is_umount_for_zygote_system_process_enabled = false;
static bool susfs_is_umount_for_zygote_iso_service_enabled = false;
extern bool susfs_hide_sus_mnts_for_all_procs;
extern void susfs_reorder_mnt_id(void);
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_BIND_MOUNT
extern bool susfs_is_auto_add_sus_bind_mount_enabled;
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_BIND_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_KSU_DEFAULT_MOUNT
extern bool susfs_is_auto_add_sus_ksu_default_mount_enabled;
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_KSU_DEFAULT_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_TRY_UMOUNT_FOR_BIND_MOUNT
extern bool susfs_is_auto_add_try_umount_for_bind_mount_enabled;
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_TRY_UMOUNT_FOR_BIND_MOUNT
#ifdef CONFIG_KSU_SUSFS_SUS_SU
extern bool susfs_is_sus_su_ready;
extern int susfs_sus_su_working_mode;
extern bool susfs_is_sus_su_hooks_enabled __read_mostly;
extern bool ksu_devpts_hook;
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_SU

static inline void susfs_on_post_fs_data(void) {
    struct path path;
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    if (!kern_path(DATA_ADB_UMOUNT_FOR_ZYGOTE_SYSTEM_PROCESS, 0, &path)) {
        susfs_is_umount_for_zygote_system_process_enabled = true;
        path_put(&path);
    }
    pr_info("susfs_is_umount_for_zygote_system_process_enabled: %d\n", susfs_is_umount_for_zygote_system_process_enabled);
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_BIND_MOUNT
    if (!kern_path(DATA_ADB_NO_AUTO_ADD_SUS_BIND_MOUNT, 0, &path)) {
        susfs_is_auto_add_sus_bind_mount_enabled = false;
        path_put(&path);
    }
    pr_info("susfs_is_auto_add_sus_bind_mount_enabled: %d\n", susfs_is_auto_add_sus_bind_mount_enabled);
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_BIND_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_KSU_DEFAULT_MOUNT
    if (!kern_path(DATA_ADB_NO_AUTO_ADD_SUS_KSU_DEFAULT_MOUNT, 0, &path)) {
        susfs_is_auto_add_sus_ksu_default_mount_enabled = false;
        path_put(&path);
    }
    pr_info("susfs_is_auto_add_sus_ksu_default_mount_enabled: %d\n", susfs_is_auto_add_sus_ksu_default_mount_enabled);
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_SUS_KSU_DEFAULT_MOUNT
#ifdef CONFIG_KSU_SUSFS_AUTO_ADD_TRY_UMOUNT_FOR_BIND_MOUNT
    if (!kern_path(DATA_ADB_NO_AUTO_ADD_TRY_UMOUNT_FOR_BIND_MOUNT, 0, &path)) {
        susfs_is_auto_add_try_umount_for_bind_mount_enabled = false;
        path_put(&path);
    }
    pr_info("susfs_is_auto_add_try_umount_for_bind_mount_enabled: %d\n", susfs_is_auto_add_try_umount_for_bind_mount_enabled);
#endif // #ifdef CONFIG_KSU_SUSFS_AUTO_ADD_TRY_UMOUNT_FOR_BIND_MOUNT
}

static inline bool is_some_system_uid(uid_t uid)
{
    return (uid >= 1000 && uid < 10000);
}

static inline bool is_zygote_isolated_service_uid(uid_t uid)
{
    return ((uid >= 90000 && uid < 100000) || (uid >= 1090000 && uid < 1100000));
}

static inline bool is_zygote_normal_app_uid(uid_t uid)
{
    return ((uid >= 10000 && uid < 19999) || (uid >= 1010000 && uid < 1019999));
}

#endif // #ifdef CONFIG_KSU_SUSFS

static bool ksu_kernel_umount_enabled = true;

static int kernel_umount_feature_get(u64 *value)
{
    *value = ksu_kernel_umount_enabled ? 1 : 0;
    return 0;
}

static int kernel_umount_feature_set(u64 value)
{
    bool enable = value != 0;
    ksu_kernel_umount_enabled = enable;
    pr_info("kernel_umount: set to %d\n", enable);
    return 0;
}

static const struct ksu_feature_handler kernel_umount_handler = {
    .feature_id = KSU_FEATURE_KERNEL_UMOUNT,
    .name = "kernel_umount",
    .get_handler = kernel_umount_feature_get,
    .set_handler = kernel_umount_feature_set,
};

static bool should_umount(struct path *path)
{
    if (!path) {
        return false;
    }

    if (current->nsproxy->mnt_ns == init_nsproxy.mnt_ns) {
        pr_info("ignore global mnt namespace process: %d\n", current_uid().val);
        return false;
    }
#ifdef CONFIG_KSU_SUSFS
    return susfs_is_mnt_devname_ksu(path);
#else
    if (path->mnt && path->mnt->mnt_sb && path->mnt->mnt_sb->s_type) {
        const char *fstype = path->mnt->mnt_sb->s_type->name;
        return strcmp(fstype, "overlay") == 0;
    }
    return false;
#endif
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0) || defined(KSU_HAS_PATH_UMOUNT)
static int ksu_path_umount(struct path *path, int flags)
{
    return path_umount(path, flags);
}
#define ksu_umount_mnt(__unused, path, flags)    (ksu_path_umount(path, flags))
#else
// TODO: Search a way to make this works without set_fs functions
static int ksu_sys_umount(const char *mnt, int flags)
{
    char __user *usermnt = (char __user *)mnt;
    mm_segment_t old_fs;
    int ret; // although asmlinkage long

    old_fs = get_fs();
    set_fs(KERNEL_DS);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
    ret = ksys_umount(usermnt, flags);
#else
    ret = sys_umount(usermnt, flags); // cuz asmlinkage long sys##name
#endif
    set_fs(old_fs);
    pr_info("%s was called, ret: %d\n", __func__, ret);
    return ret;
}

#define ksu_umount_mnt(mnt, __unused, flags)        \
    ({                        \
        int ret;                \
        path_put(__unused);            \
        ret = ksu_sys_umount(mnt, flags);    \
        ret;                    \
    })

#endif

#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
void try_umount(const char *mnt, bool check_mnt, int flags, uid_t uid)
#else
void try_umount(const char *mnt, bool check_mnt, int flags)
#endif
{
    struct path path;
    int ret;
    int err = kern_path(mnt, 0, &path);
    if (err) {
        return;
    }

    if (path.dentry != path.mnt->mnt_root) {
        // it is not root mountpoint, maybe umounted by others already.
        path_put(&path);
        return;
    }

    // we are only interest in some specific mounts
    if (check_mnt && !should_umount(&path)) {
        path_put(&path);
        return;
    }

#if defined(CONFIG_KSU_SUSFS_TRY_UMOUNT) && defined(CONFIG_KSU_SUSFS_ENABLE_LOG)
    if (susfs_is_log_enabled) {
        pr_info("susfs: umounting '%s' for uid: %d\n", mnt, uid);
    }
#endif

    ret = ksu_umount_mnt(mnt, &path, flags);
    if (ret) {
#ifdef CONFIG_KSU_DEBUG
        pr_info("%s: path: %s, ret: %d\n", __func__, mnt, ret);
#endif
    }
}

#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
void susfs_try_umount_all(uid_t uid) {
    susfs_try_umount(uid);
    /* For Legacy KSU only */
    try_umount("/odm", true, 0, uid);
    try_umount("/system", true, 0, uid);
    try_umount("/vendor", true, 0, uid);
    try_umount("/product", true, 0, uid);
    try_umount("/system_ext", true, 0, uid);
    // - For '/data/adb/modules' we pass 'false' here because it is a loop device that we can't determine whether 
    //   its dev_name is KSU or not, and it is safe to just umount it if it is really a mountpoint
    try_umount("/data/adb/modules", false, MNT_DETACH, uid);
    try_umount("/data/adb/kpm", false, MNT_DETACH, uid);
    /* For both Legacy KSU and Magic Mount KSU */
    try_umount("/debug_ramdisk", true, MNT_DETACH, uid);
    try_umount("/sbin", false, MNT_DETACH, uid);
    
    // try umount hosts file
    try_umount("/system/etc/hosts", false, MNT_DETACH, uid);

    // try umount lsposed dex2oat bins
    try_umount("/apex/com.android.art/bin/dex2oat64", false, MNT_DETACH, uid);
    try_umount("/apex/com.android.art/bin/dex2oat32", false, MNT_DETACH, uid);
}
#endif

struct umount_tw {
    struct callback_head cb;
    const struct cred *old_cred;
};

#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
static void umount_tw_func(struct callback_head *cb)
{
    struct umount_tw *tw = container_of(cb, struct umount_tw, cb);
    const struct cred *saved = NULL;
    if (tw->old_cred) {
        saved = override_creds(tw->old_cred);
    }

    uid_t uid = current_uid().val;

    // fixme: use `collect_mounts` and `iterate_mount` to iterate all mountpoint and
    // filter the mountpoint whose target is `/data/adb`
    try_umount("/odm", true, 0, uid);
    try_umount("/system", true, 0, uid);
    try_umount("/vendor", true, 0, uid);
    try_umount("/product", true, 0, uid);
    try_umount("/system_ext", true, 0, uid);
    try_umount("/data/adb/modules", false, MNT_DETACH, uid);
    try_umount("/data/adb/kpm", false, MNT_DETACH, uid);

    // try umount ksu temp path
    try_umount("/debug_ramdisk", false, MNT_DETACH, uid);
    try_umount("/sbin", false, MNT_DETACH, uid);

    // try umount lsposed dex2oat bins
    try_umount("/system/etc/hosts", false, MNT_DETACH, uid);

    // try umount lsposed dex2oat bins
    try_umount("/apex/com.android.art/bin/dex2oat64", false, MNT_DETACH, uid);
    try_umount("/apex/com.android.art/bin/dex2oat32", false, MNT_DETACH, uid);

    if (saved)
        revert_creds(saved);

    if (tw->old_cred)
        put_cred(tw->old_cred);

    kfree(tw);
}
#else
static void umount_tw_func(struct callback_head *cb)
{
    struct umount_tw *tw = container_of(cb, struct umount_tw, cb);
    const struct cred *saved = NULL;
    if (tw->old_cred) {
        saved = override_creds(tw->old_cred);
    }

    // fixme: use `collect_mounts` and `iterate_mount` to iterate all mountpoint and
    // filter the mountpoint whose target is `/data/adb`
    try_umount("/odm", true, 0);
    try_umount("/system", true, 0);
    try_umount("/vendor", true, 0);
    try_umount("/product", true, 0);
    try_umount("/system_ext", true, 0);
    try_umount("/data/adb/modules", false, MNT_DETACH);
    try_umount("/data/adb/kpm", false, MNT_DETACH);
    // try umount ksu temp path
    try_umount("/debug_ramdisk", false, MNT_DETACH);
    try_umount("/sbin", false, MNT_DETACH);

    try_umount("/system/etc/hosts", false, MNT_DETACH);
    // try umount lsposed dex2oat bins
    try_umount("/apex/com.android.art/bin/dex2oat64", false, MNT_DETACH);
    try_umount("/apex/com.android.art/bin/dex2oat32", false, MNT_DETACH);

    if (saved)
        revert_creds(saved);

    if (tw->old_cred)
        put_cred(tw->old_cred);

    kfree(tw);
}
#endif

static inline bool is_appuid(uid_t uid)
{
#define PER_USER_RANGE 100000
#define FIRST_APPLICATION_UID 10000
#define LAST_APPLICATION_UID 19999

    uid_t appid = uid % PER_USER_RANGE;
    return appid >= FIRST_APPLICATION_UID && appid <= LAST_APPLICATION_UID;
}

static inline bool is_unsupported_uid(uid_t uid)
{
#define LAST_APPLICATION_UID 19999
    uid_t appid = uid % 100000;
    return appid > LAST_APPLICATION_UID;
}

#ifdef CONFIG_KSU_SUSFS
int ksu_handle_umount(uid_t old_uid, uid_t new_uid)
{
    struct umount_tw *tw;

    // this hook is used for umounting overlayfs for some uid, if there isn't any module mounted, just ignore it!
    if (!ksu_module_mounted) {
        return 0;
    }

    if (!ksu_kernel_umount_enabled) {
        return 0;
    }

    if (!is_appuid(new_uid) || is_unsupported_uid(new_uid)) {
        pr_info("handle setuid ignore non application or isolated uid: %d\n", new_uid);
        return 0;
    }

    if (!ksu_uid_should_umount(new_uid)) {
        return 0;
    } else {
        pr_info("uid: %d should not umount!\n", current_uid().val);
    }

    // We only interest in process spwaned by zygote
    if (!susfs_is_sid_equal(current->cred->security, susfs_zygote_sid)) {
        return 0;
    }

    // Check if spawned process is isolated service first, and force to do umount if so  
    if (is_zygote_isolated_service_uid(new_uid) && susfs_is_umount_for_zygote_iso_service_enabled) {
        goto do_umount;
    }

    // - Since ksu maanger app uid is excluded in allow_list_arr, so ksu_uid_should_umount(manager_uid)
    //   will always return true, that's why we need to explicitly check if new_uid.val belongs to
    //   ksu manager
    if (ksu_is_manager_uid_valid() &&
        (new_uid % 1000000 == ksu_get_manager_uid())) // % 1000000 in case it is private space uid
    {
        return 0;
    }

    // Check if spawned process is normal user app and needs to be umounted
    if (likely(is_zygote_normal_app_uid(new_uid) && ksu_uid_should_umount(new_uid))) {
        goto do_umount;
    }

    // Lastly, Check if spawned process is some system process and needs to be umounted
    if (unlikely(is_some_system_uid(new_uid) && susfs_is_umount_for_zygote_system_process_enabled)) {
        goto do_umount;
    }
#if __SULOG_GATE
    ksu_sulog_report_syscall(new_uid, NULL, "setuid", NULL);
#endif

    return 0;

do_umount:
#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
    // susfs come first, and lastly umount by ksu, make sure umount in reversed order
    susfs_try_umount_all(new_uid);
#else
    tw = kmalloc(sizeof(*tw), GFP_ATOMIC);
    if (!tw)
        return 0;

    tw->old_cred = get_current_cred();
    tw->cb.func = umount_tw_func;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)
    int err = task_work_add(current, &tw->cb, TWA_RESUME);
#else
    int err = task_work_add(current, &tw->cb, true);
#endif
    if (err) {
        if (tw->old_cred) {
            put_cred(tw->old_cred);
        }
        kfree(tw);
        pr_warn("unmount add task_work failed\n");
    }
#endif // #ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT

    get_task_struct(current);

#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    // We can reorder the mnt_id now after all sus mounts are umounted
    susfs_reorder_mnt_id();
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT

    susfs_set_current_proc_umounted();

    put_task_struct(current);

#ifdef CONFIG_KSU_SUSFS_SUS_PATH
    susfs_run_sus_path_loop(new_uid);
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_PATH

    return 0;
}
#else
int ksu_handle_umount(uid_t old_uid, uid_t new_uid)
{
    struct umount_tw *tw;

    // this hook is used for umounting overlayfs for some uid, if there isn't any module mounted, just ignore it!
    if (!ksu_module_mounted) {
        return 0;
    }

    if (!ksu_kernel_umount_enabled) {
        return 0;
    }

    if (!is_appuid(new_uid) || is_unsupported_uid(new_uid)) {
        pr_info("handle setuid ignore non application or isolated uid: %d\n", new_uid);
        return 0;
    }

    if (!ksu_uid_should_umount(new_uid)) {
        return 0;
    } else {
        pr_info("uid: %d should not umount!\n", current_uid().val);
    }

    // check old process's selinux context, if it is not zygote, ignore it!
    // because some su apps may setuid to untrusted_app but they are in global mount namespace
    // when we umount for such process, that is a disaster!
    bool is_zygote_child = is_zygote(get_current_cred());
    if (!is_zygote_child) {
        pr_info("handle umount ignore non zygote child: %d\n", current->pid);
        return 0;
    }
#if __SULOG_GATE
    ksu_sulog_report_syscall(new_uid, NULL, "setuid", NULL);
#endif
    // umount the target mnt
    pr_info("handle umount for uid: %d, pid: %d\n", new_uid, current->pid);

    tw = kmalloc(sizeof(*tw), GFP_ATOMIC);
    if (!tw)
        return 0;

    tw->old_cred = get_current_cred();
    tw->cb.func = umount_tw_func;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)
    int err = task_work_add(current, &tw->cb, TWA_RESUME);
#else
    int err = task_work_add(current, &tw->cb, true);
#endif
    if (err) {
        if (tw->old_cred) {
            put_cred(tw->old_cred);
        }
        kfree(tw);
        pr_warn("unmount add task_work failed\n");
    }

    return 0;
}
#endif

void ksu_kernel_umount_init(void)
{
    if (ksu_register_feature_handler(&kernel_umount_handler)) {
        pr_err("Failed to register kernel_umount feature handler\n");
    }
}

void ksu_kernel_umount_exit(void)
{
    ksu_unregister_feature_handler(KSU_FEATURE_KERNEL_UMOUNT);
}