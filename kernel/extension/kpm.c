// SPDX-License-Identifier: GPL-2.0
/*
 * KPM extension wiring. Connects KSU_EXTENSION_KPM to the KPM
 * control surface (kernel/extension/kpm/kpm.c).
 *
 * Sticky get: CONFIG_KPM -> 1, else 0. No sticky set; userspace
 * KSU_EXTENSION_SET returns -EOPNOTSUPP.
 *
 * Forced-by-config goes through handler->apply_forced. The u64 is
 * decoded as (sub-op, arg) where sub-op = (u32)(value & 0xffffffff)
 * and arg = (u32)(value >> 32). SUKISU_KPM_VERSION is a no-op probe;
 * any other sub-op is a no-op + warning. The forced-pending bit is
 * cleared in both cases because the value has been consumed.
 *
 * The userspace ioctl entry point (ksu_extension_kpm_io) is unchanged.
 */
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/uaccess.h>

#include "extension.h"
#include "kpm/kpm.h"

#include "klog.h" // IWYU pragma: keep

int ksu_extension_kpm_get(u32 id, u64 *value)
{
#ifdef CONFIG_KPM
    *value = 1;
#else
    *value = 0;
#endif
    return 0;
}

const struct ksu_extension_handler_ops ksu_extension_kpm_ops = {
    .get = ksu_extension_kpm_get,
};

int ksu_extension_kpm_io(u32 id, void __user *raw)
{
#ifdef CONFIG_KPM
    return do_kpm(raw);
#else
    (void)raw;
    return -EOPNOTSUPP;
#endif
}

void ksu_extension_kpm_apply_forced(u32 id, u64 value)
{
    u32 op = (u32)(value & 0xffffffffu);
    u32 arg = (u32)(value >> 32);

    switch (op) {
    case SUKISU_KPM_VERSION:
#ifdef CONFIG_KPM
        pr_info("extension/kpm: forced VERSION probe, KPM is built in (arg=%u)\n", arg);
#else
        pr_warn("extension/kpm: forced VERSION probe, KPM is *not* built in (arg=%u)\n", arg);
#endif
        return;
    default:
        pr_warn("extension/kpm: forced op=%u arg=%u not honoured\n", op, arg);
        return;
    }
}
