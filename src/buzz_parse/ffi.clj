(ns buzz-parse.ffi
  "Raw bindings to the lexbor shim in native/buzz_shim.c — a thin defcfn
  layer with no logic. See native/buzz_shim.h for the ABI each binding
  mirrors. Documents, nodes, attributes and node lists are :pointer numbers."
  (:require [jolt.ffi :as ffi]))

;; --- documents ----------------------------------------------------------------
(ffi/defcfn parse*    "bp_parse"    [:string] :pointer)
(ffi/defcfn destroy   "bp_destroy"  [:pointer] :void)
(ffi/defcfn root      "bp_root"     [:pointer] :pointer)
(ffi/defcfn fragment* "bp_fragment" [:pointer :string] :pointer)

;; --- navigation ---------------------------------------------------------------
(ffi/defcfn node-type   "bp_type"        [:pointer] :int)
(ffi/defcfn first-child "bp_first_child" [:pointer] :pointer)
(ffi/defcfn next-node   "bp_next"        [:pointer] :pointer)
(ffi/defcfn tag-name    "bp_tag"         [:pointer] :string)
(ffi/defcfn data        "bp_data"        [:pointer] :string)

;; --- attributes ---------------------------------------------------------------
(ffi/defcfn first-attr "bp_first_attr" [:pointer] :pointer)
(ffi/defcfn next-attr  "bp_next_attr"  [:pointer] :pointer)
(ffi/defcfn attr-name  "bp_attr_name"  [:pointer] :string)
(ffi/defcfn attr-value "bp_attr_value" [:pointer] :string)

;; --- selectors ----------------------------------------------------------------
(ffi/defcfn select*     "bp_select"      [:pointer :string] :pointer)
(ffi/defcfn nodes-count "bp_nodes_count" [:pointer] :int)
(ffi/defcfn nodes-get   "bp_nodes_get"   [:pointer :int] :pointer)
(ffi/defcfn nodes-free  "bp_nodes_free"  [:pointer] :void)

;; --- serialization (malloc'd; release with free*) -----------------------------
(ffi/defcfn outer-html* "bp_outer_html" [:pointer] :pointer)
(ffi/defcfn inner-html* "bp_inner_html" [:pointer] :pointer)
(ffi/defcfn text*       "bp_text"       [:pointer] :pointer)
(ffi/defcfn free*       "bp_free"       [:pointer] :void)
