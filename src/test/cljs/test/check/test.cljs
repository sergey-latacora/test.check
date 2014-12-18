(ns cljs.test.check.test
  (:require [cljs.test :as test :refer-macros [deftest testing is]]
            [cljs.test.check :as tc]
            [cljs.test.check.generators :as gen]
            [cljs.test.check.properties :as prop :include-macros true]
            [cljs.test.check.rose-tree :as rose]
            [cljs.test.check.cljs-test :as ct :refer-macros [defspec]]
            [cljs.reader :as edn]))

(deftest generators-are-generators
  (testing "generator? returns true when called with a generator"
    (is (gen/generator? gen/int))
    (is (gen/generator? (gen/vector gen/int)))
    (is (gen/generator? (gen/return 5)))))


(deftest values-are-not-generators
  (testing "generator? returns false when called with a value"
    (is (not (gen/generator? 5)))
    (is (not (gen/generator? int)))
    (is (not (gen/generator? [1 2 3])))))

;; plus and 0 form a monoid
;; ---------------------------------------------------------------------------

(defn passes-monoid-properties
  [a b c]
  (and
    (= (+ 0 a) a)
    (= (+ a 0) a)
    (= (+ a (+ b c)) (+ (+ a b) c))))

(deftest plus-and-0-are-a-monoid
  (testing "+ and 0 form a monoid"
    (is
      (let [p (prop/for-all* [gen/int gen/int gen/int]
                passes-monoid-properties)]
        (:result (tc/quick-check 1000 p)))))
  ;; NOTE: no ratios in ClojureScript - David
  ;; (testing "with ratios as well"
  ;;   (is
  ;;     (let [p (prop/for-all* [gen/ratio gen/ratio gen/ratio]
  ;;               passes-monoid-properties)]
  ;;       (:result (tc/quick-check 1000 p)))))
  )

;; reverse
;; ---------------------------------------------------------------------------

(defn reverse-equal?-helper
  [l]
  (let [r (vec (reverse l))]
    (and (= (count l) (count r))
         (= (seq l) (rseq r)))))

(deftest reverse-equal?
  (testing "For all vectors L, reverse(reverse(L)) == L"
           (is (let [p (prop/for-all* [(gen/vector gen/int)] reverse-equal?-helper)]
                 (:result (tc/quick-check 1000 p))))))

;; failing reverse
;; ---------------------------------------------------------------------------

(deftest bad-reverse-test
  (testing "For all vectors L, L == reverse(L). Not true"
           (is (false?
                 (let [p (prop/for-all* [(gen/vector gen/int)] #(= (reverse %) %))]
                   (:result (tc/quick-check 1000 p)))))))

;; failing element remove
;; ---------------------------------------------------------------------------

(defn first-is-gone
  [l]
  (not (some #{(first l)} (vec (rest l)))))

(deftest bad-remove
  (testing "For all vectors L, if we remove the first element E, E should not
           longer be in the list. (This is a false assumption)"
           (is (false?
                 (let [p (prop/for-all* [(gen/vector gen/int)] first-is-gone)]
                   (:result (tc/quick-check 1000 p)))))))

;; exceptions shrink and return as result
;; ---------------------------------------------------------------------------

(def exception (js/Error. "I get caught"))

(defn exception-thrower
  [& args]
  (throw exception))

(deftest exceptions-are-caught
  (testing "Exceptions during testing are caught. They're also shrunk as long
           as they continue to throw."
           (is (= [exception [0]]
                  (let [result
                        (tc/quick-check
                          1000 (prop/for-all* [gen/int] exception-thrower))]
                    [(:result result) (get-in result [:shrunk :smallest])])))))

;; Count and concat work as expected
;; ---------------------------------------------------------------------------

(defn concat-counts-correct
  [a b]
  (= (count (concat a b))
     (+ (count a) (count b))))

(deftest count-and-concat
  (testing "For all vectors A and B:
           length(A + B) == length(A) + length(B)"
           (is (:result
                 (let [p (prop/for-all* [(gen/vector gen/int)
                                        (gen/vector gen/int)] concat-counts-correct)]
                   (tc/quick-check 1000 p))))))

;; Interpose (Count)
;; ---------------------------------------------------------------------------

(defn interpose-twice-the-length ;; (or one less)
  [v]
  (let [interpose-count (count (interpose :i v))
        original-count (count v)]
    (or
      (= (* 2 original-count) interpose-count)
      (= (dec (* 2 original-count)) interpose-count))))


(deftest interpose-creates-sequence-twice-the-length
  (testing
    "Interposing a collection with a value makes its count
    twice the original collection, or ones less."
    (is (:result
          (tc/quick-check 1000 (prop/for-all [v (gen/vector gen/int)] (interpose-twice-the-length v)))))))

;; Lists and vectors are equivalent with seq abstraction
;; ---------------------------------------------------------------------------

(defn list-vector-round-trip-equiv
  [a]
  ;; NOTE: can't use `(into '() ...)` here because that
  ;; puts the list in reverse order. clojure.test.check found that bug
  ;; pretty quickly...
  (= a (apply list (vec a))))

(deftest list-and-vector-round-trip
  (testing
    ""
    (is (:result
          (tc/quick-check
            1000 (prop/for-all*
                   [(gen/list gen/int)] list-vector-round-trip-equiv))))))

;; keyword->string->keyword roundtrip
;; ---------------------------------------------------------------------------

(defn keyword-string-roundtrip-equiv
  [k]
  (= k (keyword (name k))))

;; NOTE: this is one of the slowest due to how keywords are constructed
;; drop N to 200 - David
(deftest keyword-string-roundtrip
  (testing
    "For all keywords, turning them into a string and back is equivalent
    to the original string (save for the `:` bit)"
    (is (:result
          (tc/quick-check 100 (prop/for-all*
                                [gen/keyword] keyword-string-roundtrip-equiv))))))

;; Boolean and/or
;; ---------------------------------------------------------------------------

(deftest boolean-or
  (testing
    "`or` with true and anything else should be true"
    (is (:result (tc/quick-check
                   1000 (prop/for-all*
                          [gen/boolean] #(or % true)))))))

(deftest boolean-and
  (testing
    "`and` with false and anything else should be false"
    (is (:result (tc/quick-check
                   1000 (prop/for-all*
                          [gen/boolean] #(not (and % false))))))))

;; Sorting
;; ---------------------------------------------------------------------------

(defn elements-are-in-order-after-sorting
  [v]
  (every? identity (map <= (partition 2 1 (sort v)))))

(deftest sorting
  (testing
    "For all vectors V, sorted(V) should have the elements in order"
    (is (:result
          (tc/quick-check
            1000
            (prop/for-all*
              [(gen/vector gen/int)] elements-are-in-order-after-sorting))))))

;; Constant generators
;; ---------------------------------------------------------------------------

;; A constant generator always returns its created value
(defspec constant-generators 100
  (prop/for-all [a (gen/return 42)]
    (print "")
    (= a 42)))

(deftest constant-generators-dont-shrink
  (testing "Generators created with `gen/return` should not shrink"
    (is (= [42]
          (let [result (tc/quick-check 100
                         (prop/for-all
                           [a (gen/return 42)]
                           false))]
            (-> result :shrunk :smallest))))))

;; Tests are deterministic
;; ---------------------------------------------------------------------------

(defn vector-elements-are-unique
  [v]
  (== (count v) (count (distinct v))))

(defn unique-test
  [seed]
  (tc/quick-check 1000
    (prop/for-all*
      [(gen/vector gen/int)] vector-elements-are-unique)
    :seed seed))

(defn equiv-runs
  [seed]
  (= (unique-test seed) (unique-test seed)))

(deftest tests-are-deterministic
  (testing "If two runs are started with the same seed, they should
           return the same results."
    (is (:result
         (tc/quick-check 1000 (prop/for-all* [gen/int] equiv-runs))))))

;; Generating basic generators
;; --------------------------------------------------------------------------

(deftest generators-test
  (let [t (fn [generator pred]
            (:result (tc/quick-check 100
                       (prop/for-all [x generator]
                         (pred klass)))))]

    (testing "keyword"              (t gen/keyword keyword?))
    ;; (testing "ratio"                (t gen/ratio   clojure.lang.Ratio))
    ;; (testing "byte"                 (t gen/byte    Byte))
    ;; (testing "bytes"                (t gen/bytes   (Class/forName "[B")))

    (testing "char"                 (t gen/char                 string?))
    (testing "char-ascii"           (t gen/char-ascii           string?))
    (testing "char-alphanumeric"    (t gen/char-alphanumeric    string?))
    (testing "string"               (t gen/string               string?))
    (testing "string-ascii"         (t gen/string-ascii         string?))
    (testing "string-alphanumeric"  (t gen/string-alphanumeric  string?))

    (testing "vector" (t (gen/vector gen/int) vector?))
    (testing "list"   (t (gen/list gen/int)   list?))
    (testing "map"    (t (gen/map gen/int gen/int) map?))
    ))

;; Generating proper matrices
;; ---------------------------------------------------------------------------

(defn proper-matrix?
  "Check if provided nested vectors form a proper matrix — that is, all nested
   vectors have the same length"
  [mtx]
  (let [first-size (count (first mtx))]
    (every? (partial = first-size) (map count (rest mtx)))))

(deftest proper-matrix-test
  (testing
    "can generate proper matrices"
    (is (:result (tc/quick-check
                  100 (prop/for-all
                       [mtx (gen/vector (gen/vector gen/int 3) 3)]
                       (proper-matrix? mtx)))))))

(def bounds-and-vector
  (gen/bind (gen/tuple gen/s-pos-int gen/s-pos-int)
    (fn [[a b]]
      (let [minimum (min a b)
            maximum (max a b)]
        (gen/tuple (gen/return [minimum maximum])
          (gen/vector gen/int minimum maximum))))))

(deftest proper-vector-test
  (testing
    "can generate vectors with sizes in a provided range"
    (is (:result (tc/quick-check
                   100 (prop/for-all
                         [b-and-v bounds-and-vector]
                         (let [[[minimum maximum] v] b-and-v
                               c (count v)]
                           (and (<= c maximum)
                             (>= c minimum)))))))))

;; Tuples and Pairs retain their count during shrinking
;; ---------------------------------------------------------------------------

(defn n-int-generators
  [n]
  (vec (repeat n gen/int)))

(def tuples
  [(apply gen/tuple (n-int-generators 1))
   (apply gen/tuple (n-int-generators 2))
   (apply gen/tuple (n-int-generators 3))
   (apply gen/tuple (n-int-generators 4))
   (apply gen/tuple (n-int-generators 5))
   (apply gen/tuple (n-int-generators 6))])

(defn get-tuple-gen
  [index]
  (nth tuples (dec index)))

(defn inner-tuple-property
  [size]
  (prop/for-all [t (get-tuple-gen size)]
    false))

(defspec tuples-retain-size-during-shrinking 1000
  (prop/for-all [index (gen/choose 1 6)]
    (let [result (tc/quick-check
                   100 (inner-tuple-property index))]
      (= index (count (-> result
                        :shrunk :smallest first))))))

;; Bind works
;; ---------------------------------------------------------------------------

(def nat-vec
  (gen/such-that not-empty
    (gen/vector gen/nat)))

(def vec-and-elem
  (gen/bind nat-vec
    (fn [v]
      (gen/tuple (gen/elements v) (gen/return v)))))

(defspec element-is-in-vec 100
  (prop/for-all [[element coll] vec-and-elem]
    (some #{element} coll)))

;; fmap is respected during shrinking
;; ---------------------------------------------------------------------------

(def plus-fifty
  (gen/fmap (partial + 50) gen/nat))

(deftest f-map-respected-during-shrinking
  (testing
    "Generators created fmap should have that function applied
    during shrinking"
    (is (= [50]
           (let [result (tc/quick-check 100
                                        (prop/for-all
                                          [a plus-fifty]
                                          false))]
             (-> result :shrunk :smallest))))))

;; edn rountrips
;; ---------------------------------------------------------------------------

;; TODO: EDN round trips sometimes fail - David

(defn edn-roundtrip?
  [value]
  (= value (-> value prn-str edn/read-string)))

(defspec edn-roundtrips 50
  (prop/for-all [a gen/any]
    (edn-roundtrip? a)))

;; not-empty works
;; ---------------------------------------------------------------------------

(defspec not-empty-works 100
  (prop/for-all [v (gen/not-empty (gen/vector gen/boolean))]
    (not-empty v)))

;; no-shrink works
;; ---------------------------------------------------------------------------

(defn run-no-shrink
  [i]
  (tc/quick-check 100
    (prop/for-all [coll (gen/vector gen/nat)]
      (some #{i} coll))))

(defspec no-shrink-works 100
  (prop/for-all [i gen/nat]
    (let [result (run-no-shrink i)]
      (if (:result result)
        true
        (= (:fail result)
          (-> result :shrunk :smallest))))))

;; elements works with a variety of input
;; ---------------------------------------------------------------------------

(deftest elements-with-empty
  (is (thrown? js/Error (gen/elements ()))))

(defspec elements-with-a-set 100
  (prop/for-all [num (gen/elements #{9 10 11 12})]
    (<= 9 num 12)))


;; choose respects bounds during shrinking
;; ---------------------------------------------------------------------------

(def range-gen
  (gen/fmap (fn [[a b]]
              [(min a b) (max a b)])
            (gen/tuple gen/int gen/int)))

(defspec choose-respects-bounds-during-shrinking 100
  (prop/for-all [[mini maxi] range-gen
                 random-seed gen/nat
                 size gen/nat]
    (let [tree (gen/call-gen
                 (gen/choose mini maxi)
                 (gen/random random-seed)
                 size)]
      (every?
        #(and (<= mini %) (>= maxi %))
        (rose/seq tree)))))

;; rand-range copes with full range of longs as bounds
;; ---------------------------------------------------------------------------

;; NOTE: need to adjust for JS numerics - David

;; (deftest rand-range-copes-with-full-range-of-longs
;;   (let [[low high] (reduce
;;                     (fn [[low high :as margins] x]
;;                       (cond
;;                        (< x low) [x high]
;;                        (> x high) [low x]
;;                        :else margins))
;;                     [Long/MAX_VALUE Long/MIN_VALUE]
;;                     ; choose uses rand-range directly, reasonable proxy for its
;;                     ; guarantees
;;                     (take 1e6 (gen/sample-seq (gen/choose Long/MIN_VALUE Long/MAX_VALUE))))]
;;     (is (< low high))
;;     (is (< low Integer/MIN_VALUE))
;;     (is (> high Integer/MAX_VALUE))))

;; rand-range yields values inclusive of both lower & upper bounds provided to it
;; further, that generators that use rand-range use its full range of values
;; ---------------------------------------------------------------------------

(deftest rand-range-uses-inclusive-bounds
  (let [bounds [5 7]
        rand-range (apply partial gen/rand-range (gen/random) bounds)]
    (loop [trials 0
           bounds (set bounds)]
      (cond
       (== trials 10000)
       (is nil (str "rand-range didn't return both of its bounds after 10000 trials; "
                    "it is possible for this to fail without there being a problem, "
                    "but we should be able to rely upon probability to not bother us "
                    "too frequently."))
       (empty? bounds) (is true)
       :else (recur (inc trials) (disj bounds (rand-range)))))))

(deftest elements-generates-all-provided-values
  (let [options [:a 42 'c/d "foo"]]
    (is (->> (reductions
                 disj
                 (set options)
                 (gen/sample-seq (gen/elements options)))
             (take 10000)
             (some empty?))
        (str "elements didn't return all of its candidate values after 10000 trials; "
             "it is possible for this to fail without there being a problem, "
             "but we should be able to rely upon probability to not bother us "
             "too frequently."))))

;; shuffling a vector generates a permutation of that vector
;; ---------------------------------------------------------------------------

(def original-vector-and-permutation
  (gen/bind (gen/vector gen/int)
        #(gen/tuple (gen/return %) (gen/shuffle %))))

(defspec shuffled-vector-is-a-permutation-of-original 100
  (prop/for-all [[coll permutation] original-vector-and-permutation]
                (= (sort coll) (sort permutation))))

;; vector can generate large vectors; regression for TCHECK-49
;; ---------------------------------------------------------------------------

(deftest large-vector-test
  (is (= 100000
         (count (first (gen/sample
                        (gen/vector gen/nat 100000)
                        1))))))

;; defspec macro
;; ---------------------------------------------------------------------------

(defspec run-only-once 1 (prop/for-all* [gen/int] (constantly true)))

(defspec run-default-times (prop/for-all* [gen/int] (constantly true)))

(defspec run-with-map1 {:num-tests 1} (prop/for-all* [gen/int] (constantly true)))

(defspec run-with-map {:num-tests 1
                       :seed 1}
  (prop/for-all [a gen/int]
                (= a 0)))

(def my-defspec-options {:num-tests 1 :seed 1})

(defspec run-with-symbolic-options my-defspec-options
  (prop/for-all [a gen/int]
                (= a 0)))

(defspec run-with-no-options
  (prop/for-all [a gen/int]
                (integer? a)))