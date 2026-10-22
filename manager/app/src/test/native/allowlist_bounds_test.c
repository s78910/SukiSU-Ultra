#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
struct ksu_new_get_allow_list_cmd { uint16_t count; uint16_t total_count; uint32_t uids[0]; };
#include "allowlist_bounds.h"
void *memcpy(void *dst, const void *src, size_t n) {
    unsigned char *d = dst; const unsigned char *s = src;
    while (n--) *d++ = *s++;
    return dst;
}
struct guarded_buffer {
    uint32_t before;
    struct ksu_new_get_allow_list_cmd cmd;
    uint32_t uids[1024];
    uint32_t after;
};
int run_allowlist_tests(void) {
    int source[1024];
    for (int i = 0; i < 1024; ++i) source[i] = i + 10000;
    const int sizes[] = {-1, 0, 1, 1023, 1024, 1025, 65536};
    const uint16_t capacities[] = {0, 1, 7, 1023, 1024, 65535};
    for (unsigned int a = 0; a < sizeof(sizes)/sizeof(sizes[0]); ++a) {
        for (unsigned int b = 0; b < sizeof(capacities)/sizeof(capacities[0]); ++b) {
            struct guarded_buffer target;
            target.before = 0x12345678; target.after = 0x87654321;
            target.cmd.count = capacities[b]; target.cmd.total_count = 0xffff;
            for (int i = 0; i < 1024; ++i) target.uids[i] = 0xfefefefe;
            bool ok = copy_legacy_allowlist(&target.cmd, source, sizes[a], capacities[b]);
            bool valid = sizes[a] >= 0 && sizes[a] <= 1024;
            if (ok != valid) return 1;
            unsigned int count = valid ? (unsigned int)sizes[a] : 0;
            if (count > capacities[b]) count = capacities[b];
            if (target.cmd.count != count || target.cmd.total_count != (valid ? sizes[a] : 0)) return 2;
            if (target.before != 0x12345678 || target.after != 0x87654321) return 3;
            for (unsigned int i = 0; i < 1024; ++i) {
                if (target.uids[i] != (i < count ? (uint32_t)source[i] : 0xfefefefe)) return 4;
            }
        }
    }
    return 0;
}
