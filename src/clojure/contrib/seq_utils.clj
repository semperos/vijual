;;; seq_utils.clj -- Sequence utilities for Clojure

;; by Stuart Sierra, http://stuartsierra.com/
;; last updated March 2, 2009

;; Copyright (c) Stuart Sierra, 2008. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.


;; Change Log
;;
;; DEPRECATED in 1.2. Some functions promoted to clojure.core and some
;; moved to c.c.seq
;;
;; January 10, 2009 (Stuart Sierra):
;;
;; * BREAKING CHANGE: "includes?" now takes collection as first
;;   argument.  This is more consistent with Clojure collection
;;   functions; see discussion at http://groups.google.com/group/clojure/browse_thread/thread/8b2c8dc96b39ddd7/a8866d34b601ff43
;;

(ns
 ^{:author "Stuart Sierra (and others)",
   :deprecated "1.2"
   :doc "Sequence utilities for Clojure"}
 clojure.contrib.seq-utils
  (:import
   (java.lang.ref WeakReference)
   (java.util.concurrent LinkedBlockingQueue TimeUnit))
  (:refer-clojure :exclude [frequencies shuffle partition-by reductions partition-all group-by flatten]))

;; 'flatten' written by Rich Hickey,
;; see http://groups.google.com/group/clojure/msg/385098fabfcaad9b
(defn flatten
  "DEPRECATED. Prefer clojure.core version.
  Takes any nested combination of sequential things (lists, vectors,
  etc.) and returns their contents as a single, flat sequence.
  (flatten nil) returns nil."
  {:deprecated "1.2"}
  [x]
  (filter (complement sequential?)
          (rest (tree-seq sequential? seq x))))

(defn separate
  "Returns a vector:
   [ (filter f s), (filter (complement f) s) ]"
  [f s]
  [(filter f s) (filter (complement f) s)])

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from zero.

  (indexed '(a b c d))  =>  ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))

;; group-by written by Rich Hickey;
;; see http://paste.lisp.org/display/64190
(defn group-by
  "DEPRECATED. Prefer clojure.core version.
   Returns a sorted map of the elements of coll keyed by the result of
  f on each element. The value at each key will be a vector of the
  corresponding elements, in the order they appeared in coll."
  {:deprecated "1.2"}
  [f coll]
  (reduce
   (fn [ret x]
     (let [k (f x)]
       (assoc ret k (conj (get ret k []) x))))
   (sorted-map) coll))

;; partition-by originally written by Rich Hickey;
;; modified by Stuart Sierra
(defn partition-by
  "DEPRECATED. Prefer clojure.core version.
   Applies f to each value in coll, splitting it each time f returns
   a new value.  Returns a lazy seq of lazy seqs."
  {:deprecated "1.2"}
  [f coll]
  (when-let [s (seq coll)]
    (let [fst (first s)
          fv (f fst)
          run (cons fst (take-while #(= fv (f %)) (rest s)))]
      (lazy-seq
       (cons run (partition-by f (drop (count run) s)))))))

(defn frequencies
  "DEPRECATED. Prefer clojure.core version.
  Returns a map from distinct items in coll to the number of times
  they appear."
  {:deprecated "1.2"}
  [coll]
  (reduce (fn [counts x]
            (assoc counts x (inc (get counts x 0))))
          {} coll))

;; recursive sequence helpers by Christophe Grand
;; see http://clj-me.blogspot.com/2009/01/recursive-seqs.html
(defmacro rec-seq
  "Similar to lazy-seq but binds the resulting seq to the supplied
  binding-name, allowing for recursive expressions."
  [binding-name & body]
  `(let [s# (atom nil)]
     (reset! s# (lazy-seq (let [~binding-name @s#] ~@body)))))

(defmacro rec-cat
  "Similar to lazy-cat but binds the resulting sequence to the supplied
  binding-name, allowing for recursive expressions."
  [binding-name & exprs]
  `(rec-seq ~binding-name (lazy-cat ~@exprs)))

;; reductions by Chris Houser
;; see http://groups.google.com/group/clojure/browse_thread/thread/3edf6e82617e18e0/58d9e319ad92aa5f?#58d9e319ad92aa5f
(defn reductions
  "DEPRECATED. Prefer clojure.core version.
  Returns a lazy seq of the intermediate values of the reduction (as
  per reduce) of coll by f, starting with init."
  {:deprecated "1.2"}
  ([f coll]
   (if (seq coll)
     (rec-seq self (cons (first coll) (map f self (rest coll))))
     (cons (f) nil)))
  ([f init coll]
   (rec-seq self (cons init (map f self coll)))))

(defn rotations
  "Returns a lazy seq of all rotations of a seq"
  [x]
  (if (seq x)
    (map
     (fn [n _]
       (lazy-cat (drop n x) (take n x)))
     (iterate inc 0) x)
    (list nil)))

(defn partition-all
  "DEPRECATED. Prefer clojure.core version.
  Returns a lazy sequence of lists like clojure.core/partition, but may
  include lists with fewer than n items at the end."
  {:deprecated "1.2"}
  ([n coll]
   (partition-all n n coll))
  ([n step coll]
   (lazy-seq
    (when-let [s (seq coll)]
      (cons (take n s) (partition-all n step (drop step s)))))))

(defn shuffle
  "DEPRECATED. Prefer clojure.core version.
  Return a random permutation of coll"
  {:deprecated "1.2"}
  [coll]
  (let [l (java.util.ArrayList. coll)]
    (java.util.Collections/shuffle l)
    (seq l)))

(defn rand-elt
  "DEPRECATED. Prefer clojure.core/rand-nth.
   Return a random element of this seq"
  {:deprecated "1.2"}
  [s]
  (nth s (rand-int (count s))))

;; seq-on written by Konrad Hinsen
(defmulti seq-on
  "Returns a seq on the object s. Works like the built-in seq but as
   a multimethod that can have implementations for new classes and types."
  {:arglists '([s])}
  type)

(defmethod seq-on :default
  [s]
  (seq s))

(defn find-first
  "Returns the first item of coll for which (pred item) returns logical true.
  Consumes sequences up to the first match, will consume the entire sequence
  and return nil if no match is found."
  [pred coll]
  (first (filter pred coll)))

; based on work related to Rich Hickey's seque.
; blame Chouser for anything broken or ugly.
(defn fill-queue
  "filler-func will be called in another thread with a single arg
  'fill'.  filler-func may call fill repeatedly with one arg each
  time which will be pushed onto a queue, blocking if needed until
  this is possible.  fill-queue will return a lazy seq of the values
  filler-func has pushed onto the queue, blocking if needed until each
  next element becomes available.  filler-func's return value is ignored."
  ([filler-func & optseq]
   (let [opts (apply array-map optseq)
         apoll (:alive-poll opts 1)
         q (LinkedBlockingQueue. (:queue-size opts 1))
         NIL (Object.) ;nil sentinel since LBQ doesn't support nils
         weak-target (Object.)
         alive? (WeakReference. weak-target)
         fill (fn fill [x]
                (if (.get alive?)
                  (if (.offer q (if (nil? x) NIL x) apoll TimeUnit/SECONDS)
                    x
                    (recur x))
                  (throw (Exception. "abandoned"))))
         f (future
             (try
               (filler-func fill)
               (finally
                 (.put q q))) ;q itself is eos sentinel
             nil)] ; set future's value to nil
     ((fn drain []
        weak-target ; force closing over this object
        (lazy-seq
         (let [x (.take q)]
           (if (identical? x q)
             @f  ;will be nil, touch just to propagate errors
             (cons (if (identical? x NIL) nil x)
                   (drain))))))))))

(defn positions
  "Returns a lazy sequence containing the positions at which pred
   is true for items in coll."
  [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))

(defn includes?
  "Returns true if coll contains something equal (with =) to x,
  in linear time. Deprecated. Prefer 'contains?' for key testing,
  or 'some' for ad hoc linear searches."
  {:deprecated "1.2"}
  [coll x]
  (boolean (some (fn [y] (= y x)) coll)))
