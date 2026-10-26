(ns primes-sieve.c2)

(defn- sieve [n table]
  (lazy-seq
   (if-let [factors (get table n)]
     (let [next-table (reduce (fn [t step]
                                (update t (+ n step) (fnil conj []) step))
                              (dissoc table n)
                              factors)]
       (sieve (inc n) next-table))
     (cons n (sieve (inc n) (assoc table (* n n) [n]))))))

(def infinite-primes (sieve 2 {}))