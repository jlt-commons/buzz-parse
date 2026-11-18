#ifndef BUZZ_PARSE_SHIM_H
#define BUZZ_PARSE_SHIM_H

#include <stddef.h>

/* Flat C ABI over the lexbor pieces buzz-parse needs. Documents, nodes,
 * attributes and node lists cross as opaque void*. Strings returned as
 * const char* are owned by the document and NUL-terminated; strings returned
 * as char* are malloc'd copies the caller releases with bp_free. */

#if defined(_WIN32)
#define BP_API __declspec(dllexport)
#elif defined(__GNUC__) || defined(__clang__)
#define BP_API __attribute__((visibility("default")))
#else
#define BP_API
#endif

/* documents */
BP_API void* bp_parse(const char *html);          /* NULL on failure */
BP_API void  bp_destroy(void *doc);
BP_API void* bp_root(void *doc);                   /* document node */

/* fragments: parsed in a <body> context of doc; the returned node's
 * children are the fragment's top-level nodes. NULL on failure. */
BP_API void* bp_fragment(void *doc, const char *html);

/* node navigation; NULL at the end. A <template>'s children are read from
 * its content fragment. */
BP_API int         bp_type(void *node);            /* DOM nodeType */
BP_API void*       bp_first_child(void *node);
BP_API void*       bp_next(void *node);
BP_API const char* bp_tag(void *node);             /* element local name */
BP_API const char* bp_data(void *node);            /* text/comment data */

/* attributes */
BP_API void*       bp_first_attr(void *node);
BP_API void*       bp_next_attr(void *attr);
BP_API const char* bp_attr_name(void *attr);
BP_API const char* bp_attr_value(void *attr);      /* "" for a bare attribute */

/* CSS selectors: matches among root's descendants, in document order,
 * each node once. NULL if the selector does not parse. */
BP_API void*  bp_select(void *root, const char *selector);
BP_API int    bp_nodes_count(void *nodes);
BP_API void*  bp_nodes_get(void *nodes, int i);
BP_API void   bp_nodes_free(void *nodes);

/* serialization: malloc'd, release with bp_free. NULL on failure. */
BP_API char* bp_outer_html(void *node);
BP_API char* bp_inner_html(void *node);
BP_API char* bp_text(void *node);
BP_API void  bp_free(void *p);

#endif
