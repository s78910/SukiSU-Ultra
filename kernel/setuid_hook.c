#include <linux/compiler.h>
#include <linux/sched/signal.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/bpf.h>
#include <linux/capability.h>
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/err.h>
#include <linux/fs.h>
#include <linux/init.h>
#include <linux/init_task.h>
#include <linux/kallsyms.h>
#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/mm.h>
#include <linux/mount.h>
#include <linux/namei.h>
#include <linux/nsproxy.h>
#include <linux/path.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/security.h>
#include <linux/stddef.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>
#include <linux/version.h>
#include <linux/lsm_hooks.h>
#include <linux/binfmts.h>
#include <linux/tty.h>

#ifndef KSU_HAS_PATH_UMOUNT
#include <linux/syscalls.h> // sys_umount (<4.17) & ksys_umount (4.17+)
#endif

#ifdef MODULE
#include <linux/list.h>
#include <linux/irqflags.h>
#include <linux/mm_types.h>
#include <linux/rcupdate.h>
#include <linux/vmalloc.h>
#endif

#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#include <linux/susfs_def.h>
#endif // #ifdef CONFIG_KSU_SUSFS

#include "allowlist.h"
#include "setuid_hook.h"
#include "feature.h"
#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "manager.h"
#include "selinux/selinux.h"
#include "seccomp_cache.h"
#include "supercalls.h"
#include "syscall_hook_manager.h"
#include "kernel_umount.h"

#include "sulog.h"

static bool ksu_enhanced_security_enabled = false;

static int enhanced_security_feature_get(u64 *value)
{
    *value = ksu_enhanced_security_enabled ? 1 : 0;
    return 0;
}

static int enhanced_security_feature_set(u64 value)
{
    bool enable = value != 0;
    ksu_enhanced_security_enabled = enable;
    pr_info("enhanced_security: set to %d\n", enable);
    return 0;
}

static const struct ksu_feature_handler enhanced_security_handler = {
    .feature_id = KSU_FEATURE_ENHANCED_SECURITY,
    .name = "enhanced_security",
    .get_handler = enhanced_security_feature_get,
    .set_handler = enhanced_security_feature_set,
};

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

static inline bool is_allow_su(void)
{
    if (is_manager()) {
        // we are manager, allow!
        return true;
    }
    return ksu_is_allow_uid_for_current(current_uid().val);
}

static inline bool is_unsupported_uid(uid_t uid)
{
#define LAST_APPLICATION_UID 19999
    uid_t appid = uid % 100000;
    return appid > LAST_APPLICATION_UID;
}

#if __SULOG_GATE
static void sulog_prctl_cmd(uid_t uid, unsigned long cmd)
{
    const char *name = NULL;

    switch (cmd) {

#ifdef CONFIG_KSU_SUSFS
    case CMD_SUSFS_ADD_SUS_PATH:            name = "prctl_susfs_add_sus_path"; break;
    case CMD_SUSFS_ADD_SUS_PATH_LOOP:       name = "prctl_susfs_add_sus_path_loop"; break;
    case CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH: name = "prctl_susfs_set_android_data_root_path"; break;
    case CMD_SUSFS_SET_SDCARD_ROOT_PATH:    name = "prctl_susfs_set_sdcard_root_path"; break;
    case CMD_SUSFS_ADD_SUS_MOUNT:           name = "prctl_susfs_add_sus_mount"; break;
    case CMD_SUSFS_HIDE_SUS_MNTS_FOR_ALL_PROCS: name = "prctl_susfs_hide_sus_mnts_for_all_procs"; break;
    case CMD_SUSFS_UMOUNT_FOR_ZYGOTE_ISO_SERVICE: name = "prctl_susfs_umount_for_zygote_iso_service"; break;
    case CMD_SUSFS_ADD_SUS_KSTAT:           name = "prctl_susfs_add_sus_kstat"; break;
    case CMD_SUSFS_UPDATE_SUS_KSTAT:        name = "prctl_susfs_update_sus_kstat"; break;
    case CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY: name = "prctl_susfs_add_sus_kstat_statically"; break;
    case CMD_SUSFS_ADD_TRY_UMOUNT:          name = "prctl_susfs_add_try_umount"; break;
    case CMD_SUSFS_SET_UNAME:               name = "prctl_susfs_set_uname"; break;
    case CMD_SUSFS_ENABLE_LOG:              name = "prctl_susfs_enable_log"; break;
    case CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG: name = "prctl_susfs_set_cmdline_or_bootconfig"; break;
    case CMD_SUSFS_ADD_OPEN_REDIRECT:       name = "prctl_susfs_add_open_redirect"; break;
    case CMD_SUSFS_SHOW_VERSION:            name = "prctl_susfs_show_version"; break;
    case CMD_SUSFS_SHOW_ENABLED_FEATURES:   name = "prctl_susfs_show_enabled_features"; break;
    case CMD_SUSFS_SHOW_VARIANT:            name = "prctl_susfs_show_variant"; break;
#ifdef CONFIG_KSU_SUSFS_SUS_SU
    case CMD_SUSFS_SUS_SU:                  name = "prctl_susfs_sus_su"; break;
    case CMD_SUSFS_IS_SUS_SU_READY:         name = "prctl_susfs_is_sus_su_ready"; break;
    case CMD_SUSFS_SHOW_SUS_SU_WORKING_MODE: name = "prctl_susfs_show_sus_su_working_mode"; break;
#endif
    case CMD_SUSFS_ADD_SUS_MAP:             name = "prctl_susfs_add_sus_map"; break;
    case CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING: name = "prctl_susfs_enable_avc_log_spoofing"; break;
#endif

    default:                                name = "prctl_unknown"; break;
    }

    ksu_sulog_report_syscall(uid, NULL, name, NULL);
}
#endif

int ksu_handle_prctl(int option, unsigned long arg2, unsigned long arg3,
             unsigned long arg4, unsigned long arg5)
{


#ifdef CONFIG_KSU_SUSFS
    // - We straight up check if process is supposed to be umounted, return 0 if so
    // - This is to prevent side channel attack as much as possible
    if (likely(susfs_is_current_proc_umounted()))
        return 0;
#endif

    // if success, we modify the arg5 as result!
    u32 *result = (u32 *)arg5;
    u32 reply_ok = KERNEL_SU_OPTION;

    if (KERNEL_SU_OPTION != option) {
        return 0;
    }

    bool from_root = 0 == current_uid().val;
    bool from_manager = is_manager();
    
#if __SULOG_GATE
    sulog_prctl_cmd(current_uid().val, arg2);
#endif

    if (!from_root && !from_manager && !is_allow_su()) {
        // only root or manager can access this interface
        return 0;
    }

#ifdef CONFIG_KSU_DEBUG
    pr_info("option: 0x%x, cmd: %ld\n", option, arg2);
#endif

#ifdef CONFIG_KSU_SUSFS
    int susfs_cmd_err = 0;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
    if (arg2 == CMD_SUSFS_ADD_SUS_PATH) {
        susfs_cmd_err = susfs_add_sus_path((struct st_susfs_sus_path __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_PATH -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_ADD_SUS_PATH_LOOP) {
        susfs_cmd_err = susfs_add_sus_path_loop((struct st_susfs_sus_path __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_PATH_LOOP -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH) {
        susfs_cmd_err = susfs_set_i_state_on_external_dir((char __user*)arg3, CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH);
        pr_info("susfs: CMD_SUSFS_SET_ANDROID_DATA_ROOT_PATH -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_SET_SDCARD_ROOT_PATH) {
        susfs_cmd_err = susfs_set_i_state_on_external_dir((char __user*)arg3, CMD_SUSFS_SET_SDCARD_ROOT_PATH);
        pr_info("susfs: CMD_SUSFS_SET_SDCARD_ROOT_PATH -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SUS_PATH
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    if (arg2 == CMD_SUSFS_ADD_SUS_MOUNT) {
        susfs_cmd_err = susfs_add_sus_mount((struct st_susfs_sus_mount __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_MOUNT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_HIDE_SUS_MNTS_FOR_ALL_PROCS) {
        if (arg3 != 0 && arg3 != 1) {
            pr_err("susfs: CMD_SUSFS_HIDE_SUS_MNTS_FOR_ALL_PROCS -> arg3 can only be 0 or 1\n");
            return 0;
        }
        susfs_hide_sus_mnts_for_all_procs = arg3;
        pr_info("susfs: CMD_SUSFS_HIDE_SUS_MNTS_FOR_ALL_PROCS -> susfs_hide_sus_mnts_for_all_procs: %lu\n", arg3);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_UMOUNT_FOR_ZYGOTE_ISO_SERVICE) {
        if (arg3 != 0 && arg3 != 1) {
            pr_err("susfs: CMD_SUSFS_UMOUNT_FOR_ZYGOTE_ISO_SERVICE -> arg3 can only be 0 or 1\n");
            return 0;
        }
        susfs_is_umount_for_zygote_iso_service_enabled = arg3;
        pr_info("susfs: CMD_SUSFS_UMOUNT_FOR_ZYGOTE_ISO_SERVICE -> susfs_is_umount_for_zygote_iso_service_enabled: %lu\n", arg3);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
    if (arg2 == CMD_SUSFS_ADD_SUS_KSTAT) {
        susfs_cmd_err = susfs_add_sus_kstat((struct st_susfs_sus_kstat __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_KSTAT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_UPDATE_SUS_KSTAT) {
        susfs_cmd_err = susfs_update_sus_kstat((struct st_susfs_sus_kstat __user*)arg3);
        pr_info("susfs: CMD_SUSFS_UPDATE_SUS_KSTAT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY) {
        susfs_cmd_err = susfs_add_sus_kstat((struct st_susfs_sus_kstat __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
    if (arg2 == CMD_SUSFS_ADD_TRY_UMOUNT) {
        susfs_cmd_err = susfs_add_try_umount((struct st_susfs_try_umount __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_TRY_UMOUNT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
    if (arg2 == CMD_SUSFS_SET_UNAME) {
        susfs_cmd_err = susfs_set_uname((struct st_susfs_uname __user*)arg3);
        pr_info("susfs: CMD_SUSFS_SET_UNAME -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
    if (arg2 == CMD_SUSFS_ENABLE_LOG) {
        if (arg3 != 0 && arg3 != 1) {
            pr_err("susfs: CMD_SUSFS_ENABLE_LOG -> arg3 can only be 0 or 1\n");
            return 0;
        }
        susfs_set_log(arg3);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
    if (arg2 == CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG) {
        susfs_cmd_err = susfs_set_cmdline_or_bootconfig((char __user*)arg3);
        pr_info("susfs: CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
    if (arg2 == CMD_SUSFS_ADD_OPEN_REDIRECT) {
        susfs_cmd_err = susfs_add_open_redirect((struct st_susfs_open_redirect __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_OPEN_REDIRECT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
#ifdef CONFIG_KSU_SUSFS_SUS_SU
    if (arg2 == CMD_SUSFS_SUS_SU) {
        susfs_cmd_err = susfs_sus_su((struct st_sus_su __user*)arg3);
        pr_info("susfs: CMD_SUSFS_SUS_SU -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS_SUS_SU
    if (arg2 == CMD_SUSFS_SHOW_VERSION) {
        int len_of_susfs_version = strlen(SUSFS_VERSION);
        char *susfs_version = SUSFS_VERSION;

        susfs_cmd_err = copy_to_user((void __user*)arg3, (void*)susfs_version, len_of_susfs_version+1);
        pr_info("susfs: CMD_SUSFS_SHOW_VERSION -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_SHOW_ENABLED_FEATURES) {
        if (arg4 <= 0) {
            pr_err("susfs: CMD_SUSFS_SHOW_ENABLED_FEATURES -> arg4 cannot be <= 0\n");
            return 0;
        }
        susfs_cmd_err = susfs_get_enabled_features((char __user*)arg3, arg4);
        pr_info("susfs: CMD_SUSFS_SHOW_ENABLED_FEATURES -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_SHOW_VARIANT) {
        int len_of_variant = strlen(SUSFS_VARIANT);
        char *susfs_variant = SUSFS_VARIANT;

        susfs_cmd_err = copy_to_user((void __user*)arg3, (void*)susfs_variant, len_of_variant+1);
        pr_info("susfs: CMD_SUSFS_SHOW_VARIANT -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#ifdef CONFIG_KSU_SUSFS_SUS_SU
    if (arg2 == CMD_SUSFS_IS_SUS_SU_READY) {
        susfs_cmd_err = copy_to_user((void __user*)arg3, (void*)&susfs_is_sus_su_ready, sizeof(susfs_is_sus_su_ready));
        pr_info("susfs: CMD_SUSFS_IS_SUS_SU_READY -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
    if (arg2 == CMD_SUSFS_SHOW_SUS_SU_WORKING_MODE) {
        int working_mode = susfs_get_sus_su_working_mode();

        susfs_cmd_err = copy_to_user((void __user*)arg3, (void*)&working_mode, sizeof(working_mode));
        pr_info("susfs: CMD_SUSFS_SHOW_SUS_SU_WORKING_MODE -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_SU
#ifdef CONFIG_KSU_SUSFS_SUS_MAP
    if (arg2 == CMD_SUSFS_ADD_SUS_MAP) {
        susfs_cmd_err = susfs_add_sus_map((struct st_susfs_sus_map __user*)arg3);
        pr_info("susfs: CMD_SUSFS_ADD_SUS_MAP -> ret: %d\n", susfs_cmd_err);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
                pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MAP
    if (arg2 == CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING) {
        if (arg3 != 0 && arg3 != 1) {
            pr_err("susfs: CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING -> arg3 can only be 0 or 1\n");
            return 0;
        }
        susfs_set_avc_log_spoofing(arg3);
        if (copy_to_user((void __user*)arg5, &susfs_cmd_err, sizeof(susfs_cmd_err)))
            pr_info("susfs: copy_to_user() failed\n");
        return 0;
    }
#endif //#ifdef CONFIG_KSU_SUSFS

    return 0;
}

static bool is_appuid(uid_t uid)
{
#define PER_USER_RANGE 100000
#define FIRST_APPLICATION_UID 10000
#define LAST_APPLICATION_UID 19999

    uid_t appid = uid % PER_USER_RANGE;
    return appid >= FIRST_APPLICATION_UID && appid <= LAST_APPLICATION_UID;
}

static bool should_umount(struct path *path)
{
    if (!path) {
        return false;
    }

    if (current->nsproxy->mnt_ns == init_nsproxy.mnt_ns) {
        pr_info("ignore global mnt namespace process: %d\n",
            current_uid().val);
        return false;
    }

    if (path->mnt && path->mnt->mnt_sb && path->mnt->mnt_sb->s_type) {
        const char *fstype = path->mnt->mnt_sb->s_type->name;
        return strcmp(fstype, "overlay") == 0;
    }
    return false;
}
extern int path_umount(struct path *path, int flags);
static void ksu_umount_mnt(struct path *path, int flags)
{
    int err = path_umount(path, flags);
    if (err) {
        pr_info("umount %s failed: %d\n", path->dentry->d_iname, err);
    }
}

#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
void try_umount(const char *mnt, bool check_mnt, int flags, uid_t uid)
#else
static void try_umount(const char *mnt, bool check_mnt, int flags)
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

#ifdef CONFIG_KSU_SUSFS
int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
    uid_t new_uid = ruid;
	uid_t old_uid = current_uid().val;
    pr_info("handle_setuid from %d to %d\n", old_uid, new_uid);

    if (0 != old_uid) {
        // old process is not root, ignore it.
        if (ksu_enhanced_security_enabled) {
            // disallow any non-ksu domain escalation from non-root to root!
            if (unlikely(new_uid) == 0) {
                if (!is_ksu_domain()) {
                    pr_warn("find suspicious EoP: %d %s, from %d to %d\n", 
                        current->pid, current->comm, old_uid, new_uid);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 2, 0)
                    force_sig(SIGKILL);
#else
                    force_sig(SIGKILL, current);
#endif
                    return 0;
                }
            }
            // disallow appuid decrease to any other uid if it is allowed to su
            if (is_appuid(old_uid)) {
                if (new_uid < old_uid && !ksu_is_allow_uid_for_current(old_uid)) {
                    pr_warn("find suspicious EoP: %d %s, from %d to %d\n", 
                        current->pid, current->comm, old_uid, new_uid);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 2, 0)
                    force_sig(SIGKILL);
#else
                    force_sig(SIGKILL, current);
#endif
                    return 0;
                }
            }
        }
        return 0;
    }

    if (new_uid == 2000) {
        ksu_set_task_tracepoint_flag(current);
    }

    if (!is_appuid(new_uid) || is_unsupported_uid(new_uid)) {
        pr_info("handle setuid ignore non application or isolated uid: %d\n", new_uid);
        ksu_clear_task_tracepoint_flag(current);
        return 0;
    }

    // if on private space, see if its possibly the manager
    if (unlikely(new_uid > 100000 && new_uid % 100000 == ksu_get_manager_uid())) {
         ksu_set_manager_uid(new_uid);
    }

    if (unlikely(ksu_get_manager_uid() == new_uid)) {
        pr_info("install fd for manager: %d\n", new_uid);
        ksu_install_fd();
        spin_lock_irq(&current->sighand->siglock);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 2) // Android backport this feature in 5.10.2
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
#else
        // we dont have those new fancy things upstream has
	    // lets just do original thing where we disable seccomp
        disable_seccomp();
#endif
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);
        return 0;
    }

    if (unlikely(ksu_is_allow_uid_for_current(new_uid))) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER &&
            current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 2) // Android backport this feature in 5.10.2
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
#else
            // we don't have those new fancy things upstream has
            // lets just do original thing where we disable seccomp
            disable_seccomp();
#endif
            spin_unlock_irq(&current->sighand->siglock);
        }
        ksu_set_task_tracepoint_flag(current);
    } else {
        ksu_clear_task_tracepoint_flag(current);
    }

    // Handle kernel umount
    
    // We only interest in process spwaned by zygote
    if (!susfs_is_sid_equal(old->security, susfs_zygote_sid)) {
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
    ksu_handle_umount(old_uid, new_uid);

#endif // #ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT

    get_task_struct(current);

#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    // We can reorder the mnt_id now after all sus mounts are umounted
    susfs_reorder_mnt_id();
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT

    susfs_set_current_proc_umounted();

    put_task_struct(current);

#ifdef CONFIG_KSU_SUSFS_SUS_PATH
    susfs_run_sus_path_loop(new_uid.val);
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_PATH
    return 0;
}
#else
int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
    uid_t new_uid = ruid;
	uid_t old_uid = current_uid().val;
    pr_info("handle_setuid from %d to %d\n", old_uid, new_uid);

    if (0 != old_uid) {
        // old process is not root, ignore it.
        if (ksu_enhanced_security_enabled) {
            // disallow any non-ksu domain escalation from non-root to root!
            if (unlikely(new_uid) == 0) {
                if (!is_ksu_domain()) {
                    pr_warn("find suspicious EoP: %d %s, from %d to %d\n", 
                        current->pid, current->comm, old_uid, new_uid);
                    force_sig(SIGKILL);
                    return 0;
                }
            }
            // disallow appuid decrease to any other uid if it is allowed to su
            if (is_appuid(old_uid)) {
                if (new_uid < old_uid && !ksu_is_allow_uid_for_current(old_uid)) {
                    pr_warn("find suspicious EoP: %d %s, from %d to %d\n", 
                        current->pid, current->comm, old_uid, new_uid);
                    force_sig(SIGKILL);
                    return 0;
                }
            }
        }
        return 0;
    }

    if (new_uid == 2000) {
        ksu_set_task_tracepoint_flag(current);
    }

    if (!is_appuid(new_uid) || is_unsupported_uid(new_uid)) {
        pr_info("handle setuid ignore non application or isolated uid: %d\n", new_uid);
        ksu_clear_task_tracepoint_flag(current);
        return 0;
    }

    // if on private space, see if its possibly the manager
    if (unlikely(new_uid > 100000 && new_uid % 100000 == ksu_get_manager_uid())) {
         ksu_set_manager_uid(new_uid);
    }

    if (unlikely(ksu_get_manager_uid() == new_uid)) {
        pr_info("install fd for manager: %d\n", new_uid);
        ksu_install_fd();
        spin_lock_irq(&current->sighand->siglock);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 2) // Android backport this feature in 5.10.2
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
#else
        // we dont have those new fancy things upstream has
	    // lets just do original thing where we disable seccomp
        disable_seccomp();
#endif
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);
        return 0;
    }

    if (unlikely(ksu_is_allow_uid_for_current(new_uid))) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER &&
            current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 2) // Android backport this feature in 5.10.2
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
#else
            // we don't have those new fancy things upstream has
            // lets just do original thing where we disable seccomp
            disable_seccomp();
#endif
            spin_unlock_irq(&current->sighand->siglock);
        }
        ksu_set_task_tracepoint_flag(current);
    } else {
        ksu_clear_task_tracepoint_flag(current);
    }

    // Handle kernel umount
    ksu_handle_umount(old_uid, new_uid);
    
#if __SULOG_GATE
    ksu_sulog_report_syscall(new_uid, NULL, "setuid", NULL);
#endif

    return 0;
}
#endif // #ifdef CONFIG_KSU_SUSFS


static int ksu_task_prctl(int option, unsigned long arg2, unsigned long arg3,
              unsigned long arg4, unsigned long arg5)
{
    ksu_handle_prctl(option, arg2, arg3, arg4, arg5);
    return -ENOSYS;
}

// kernel 4.4 and 4.9
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0) ||    \
    defined(CONFIG_IS_HW_HISI) ||    \
    defined(CONFIG_KSU_ALLOWLIST_WORKAROUND)
static int ksu_key_permission(key_ref_t key_ref, const struct cred *cred,
                  unsigned perm)
{
    if (init_session_keyring != NULL) {
        return 0;
    }
    if (strcmp(current->comm, "init")) {
        // we are only interested in `init` process
        return 0;
    }
    init_session_keyring = cred->session_keyring;
    pr_info("kernel_compat: got init_session_keyring\n");
    return 0;
}
#endif

#ifndef MODULE
static struct security_hook_list ksu_hooks[] = {
    LSM_HOOK_INIT(task_prctl, ksu_task_prctl),
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0) || \
    defined(CONFIG_IS_HW_HISI) || defined(CONFIG_KSU_ALLOWLIST_WORKAROUND)
    LSM_HOOK_INIT(key_permission, ksu_key_permission)
#endif
};

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 8, 0)
const struct lsm_id ksu_lsmid = {
    .name = "ksu",
    .id = 912,
};
#endif

void __init ksu_lsm_hook_init(void)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 8, 0)
    // https://elixir.bootlin.com/linux/v6.8/source/include/linux/lsm_hooks.h#L120
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), &ksu_lsmid);
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), "ksu");
#else
    // https://elixir.bootlin.com/linux/v4.10.17/source/include/linux/lsm_hooks.h#L1892
    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks));
#endif
}
#endif

void ksu_setuid_hook_init(void)
{
    ksu_lsm_hook_init();
    ksu_kernel_umount_init();
    if (ksu_register_feature_handler(&enhanced_security_handler)) {
        pr_err("Failed to register enhanced security feature handler\n");
    }
}

void ksu_setuid_hook_exit(void)
{
    pr_info("ksu_core_exit\n");
    ksu_kernel_umount_exit();
    ksu_unregister_feature_handler(KSU_FEATURE_ENHANCED_SECURITY);
}
