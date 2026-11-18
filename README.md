# buzz-parse

An HTML parser for [jolt](https://jolt-lang.net) built on
[lexbor](https://github.com/lexbor/lexbor), with the API of
[crouton](https://github.com/weavejester/crouton), plus CSS selectors and
fragment parsing.

```clojure
(require '[buzz-parse.html :as html])

(html/parse "page.html")   ; anything clojure.core/slurp accepts
(html/parse-string "<html><head></head><body></body></html>")
;=> {:tag :html :attrs nil
;    :content [{:tag :head :attrs nil :content nil}
;              {:tag :body :attrs nil :content nil}]}
```

The tree is the one `clojure.xml/parse` produces, so `clojure.zip/xml-zip`
and friends work on it.

## Installation

```clojure
;; deps.edn
{:deps {io.github.jlt-commons/buzz-parse {:git/url "https://github.com/jlt-commons/buzz-parse"
                                          :git/sha "<sha>"}}}
```

Needs jolt 0.8.26+, a C compiler and perl. The first run builds the native
library inside the gitlib checkout, cloning the pinned lexbor commit unless
`LEXBOR_HOME` points at a checkout. `jolt build` links it statically.

## API

`(parse source)`, `(parse-string s)`: the whole document as a tree. Matches
crouton (which used jsoup): html/head/body are synthesized when missing,
comments and doctypes are dropped, adjacent text is joined, text whitespace
collapses to single spaces except inside `<script>` and `<style>`, and empty
`:attrs`/`:content` are `nil`.

`(parse-fragment s)`: parses `s` as the contents of a `<body>` and returns a
vector of its top-level nodes, without the html/head/body wrapper.

```clojure
(html/parse-fragment "Hi <b>there</b>!")
;=> ["Hi " {:tag :b :attrs nil :content ["there"]} "!"]
```

`(select html selector)`, `(select html selector {:as ...})`: parses the
string `html` and returns a vector of the nodes matching a CSS selector, in
document order, each once. lexbor supports selectors level 4, including
`:has()`, `:is()`, `:not()`, `:nth-child(… of S)` and case-insensitive
attribute matches. A selector that does not parse throws `ex-info`.

```clojure
(def page "<ul><li class=a>One</li><li>Two <b>2</b></li></ul>")

(html/select page "li.a")                         ;=> [{:tag :li :attrs {:class "a"} :content ["One"]}]
(html/select page "li:nth-child(2)" {:as :html})  ;=> ["<li>Two <b>2</b></li>"]
(html/select page "li:has(b)" {:as :inner-html})  ;=> ["Two <b>2</b>"]
(html/select page "ul" {:as :text})               ;=> ["OneTwo 2"]
```

`:as` is `:tree` (default), `:html` (outer HTML), `:inner-html` or `:text`
(text content, whitespace kept).

## Command line

Each release carries standalone binaries for macOS (arm64), Linux (x86_64)
and Windows (x86_64). Or run it from source with `jolt -m buzz-parse.cli`.

```bash
buzz-parse page.html                        # EDN tree
curl -s https://example.com | buzz-parse --format json
buzz-parse page.html -s 'a[href]' --as html # one match per line
buzz-parse page.html -s 'h1, h2' --as text
echo 'a <b>b</b>' | buzz-parse --fragment
```

`buzz-parse --help` lists the options. Bad usage, a missing file or a
selector that doesn't parse exits 2.

## Development

```bash
jolt run native   # build native/libbuzz_parse.{dylib,so,a}
jolt run test     # build if needed, then run the tests
jolt run dist     # standalone binary at target/buzz-parse
ci/smoke.sh target/buzz-parse
```

CI (`.github/workflows/ci.yml`) runs the tests on Linux and macOS, then builds
and smoke-tests a binary on macOS, Linux and Windows. Pushing a `v*` tag, or
running the workflow on main with a version, publishes those binaries as a
GitHub release.

`native/buzz_shim.c` includes lexbor's html, css and selectors modules,
amalgamated by lexbor's `single.pl` into `native/build/lexbor_single.h`, and
exposes a flat `bp_*` API that `buzz-parse.ffi` binds. Set `LEXBOR_HOME` to
build against a local lexbor checkout.

## License

Apache License 2.0, the same as lexbor.
