;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Gary Fredericks"
      :doc "clojure.spec specs for test.check"}
    clojure.test.check.specs
  (:require [clojure.test.check :as t.c]
            [clojure.test.check.generators :as gen]
            [clojure.spec :as s]))

(defmulti reporter-fn-return :type)
; (defmethod reporter-fn-return )

(s/def ::t.c/seed int?)
(s/def ::t.c/max-size nat-int?)
(s/def ::t.c/reporter-fn
  (s/fspec :args (s/cat :arg (s/keys))
           :ret any?))
(s/def ::t.c/result any?)
(s/def ::t.c/num-tests nat-int?)
(s/def ::t.c/failing-size nat-int?)
(s/def ::t.c/fail ::t.c/result)
(s/def ::t.c/total-nodes-visited nat-int?)
(s/def ::t.c/depth nat-int?)
(s/def ::t.c/smallest vector?)
(s/def ::t.c/shrunk (s/keys :req-un [::t.c/total-nodes-visited
                                     ::t.c/depth ::t.c/result
                                     ::t.c/smallest]))

(s/def ::quick-check-ret
  (s/keys :req-un [::t.c/result ::t.c/num-tests ::t.c/seed]
          :opt-un [::t.c/failing-size ::t.c/fail ::t.c/shrunk]))

;; worth doing better than this?
(def property? gen/generator?)

(s/fdef t.c/quick-check
        :args (s/cat :num-tests number?
                     :property property?
                     :opts (s/keys* :opt-un [::t.c/seed
                                             ::t.c/max-size
                                             ::t.c/reporter-fn]))
        :ret ::quick-check-ret)

;;
;; Generators
;;

(s/def ::gen/fmap-fn (s/fspec :args (s/cat :arg any?) :ret any?))
(s/def ::gen/size (s/and number? #(<= 0 %) #(< % Double/POSITIVE_INFINITY)))

(s/fdef gen/fmap
        :args (s/cat :f ifn? #_::gen/fmap-fn ;; TODO: why can't I?
                     :gen gen/generator?)
        :ret gen/generator?)
(s/fdef gen/bind
        :args (s/cat :gen gen/generator?
                     :f ifn?)
        :ret gen/generator?)
(s/fdef gen/sample
        :args (s/cat :gen gen/generator?
                     :num-samples (s/? nat-int?))
        :ret coll?)
(s/fdef gen/generate
        :args (s/cat :gen gen/generator?
                     :size (s/? ::gen/size))
        :ret any?)
(s/fdef gen/sized
        :args (s/cat :sized-gen ifn?
                     ;; TODO: why can't I?
                     #_(s/fspec :args (s/cat :size ::gen/size)
                                         :ret gen/generator?))
        :ret gen/generator?)
(s/fdef gen/resize
        :args (s/cat :n ::gen/size
                     :generator gen/generator?)
        :ret gen/generator?)
(s/fdef gen/scale
        :args (s/cat :f (s/fspec :args (s/cat :size ::gen/size)
                                 :ret ::gen/size)
                     :gen gen/generator?)
        :ret gen/generator?)
(s/fdef gen/choose
        :args (s/cat :lower int?
                     :upper int?)
        :ret gen/generator?)
(s/fdef gen/one-of
        :args (s/cat :gens (s/spec (s/cat :gens (s/+ gen/generator?))))
        :ret gen/generator?)
(s/fdef gen/frequency
        :args (s/cat :pairs (s/coll-of
                             (s/tuple pos-int? gen/generator?)))
        :ret gen/generator?)
(s/fdef gen/elements
        :args (s/cat :coll (s/spec
                            (s/cat :elements (s/+ any?))))
        :ret gen/generator?)
(s/def ::gen/max-tries pos-int?)
(s/def ::gen/pred
  (s/with-gen ifn?
    #(gen/return (constantly true))))
(s/def ::gen/gen
  (s/with-gen gen/generator?
    #(gen/return (gen/return 42))))
(s/def ::gen/ex-fn
  (s/fspec :args (s/cat :arg (s/keys :req-un [::gen/max-tries
                                              ::gen/pred
                                              ::gen/gen]))
           :ret #(instance? Throwable %)))
(s/fdef gen/such-that
        :args (s/cat :pred ifn?
                     :gen gen/generator?
                     :max-tries-or-opts (s/?
                                         (s/alt :max-tries nat-int?
                                                :opts (s/keys :opt-un
                                                              [::gen/max-tries
                                                               ::gen/ex-fn]))))
        :ret gen/generator?)

(s/fdef gen/not-empty
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/no-shrink
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/shrink-2
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/tuple
        :args (s/cat :gens (s/* gen/generator?))
        :ret gen/generator?)

(s/fdef gen/vector
        :args (s/cat :gen gen/generator?
                     :size-opts (s/? (s/alt :num-elements nat-int?
                                            ;; TODO: use s/and somehow to say max >= min?
                                            :min-and-max (s/cat :min-elements nat-int?
                                                                :max-elements nat-int?))))
        :ret gen/generator?)

(s/fdef gen/list
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

;; TODO: Don't commit this
(clojure.spec.test/instrument)
