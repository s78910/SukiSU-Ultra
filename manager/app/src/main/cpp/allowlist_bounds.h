#ifndef BAKASU_ALLOWLIST_BOUNDS_H
#define BAKASU_ALLOWLIST_BOUNDS_H

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define KSU_ALLOWLIST_CAPACITY 1024

/* ksu_new_get_allow_list_cmd is the unchanged upstream UAPI type. */
static inline bool copy_legacy_allowlist(struct ksu_new_get_allow_list_cmd *cmd,
                                       const int *uids, int size, uint16_t capacity) {
    cmd->count = 0;
    cmd->total_count = 0;
    if (size < 0 || size > KSU_ALLOWLIST_CAPACITY) {
        return false;
    }
    cmd->total_count = (uint16_t) size;
    uint16_t bounded = capacity < KSU_ALLOWLIST_CAPACITY ? capacity : KSU_ALLOWLIST_CAPACITY;
    cmd->count = bounded < size ? bounded : (uint16_t) size;
    if (cmd->count > 0) {
        memcpy(cmd->uids, uids, sizeof(*uids) * cmd->count);
    }
    return true;
}

#endif
