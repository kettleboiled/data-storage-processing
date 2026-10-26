(ns philosophers.core)

(defn create-forks [n]
  (vec (repeatedly n #(ref {:uses 0 :free true}))))

(defn philosopher
  [id left-fork right-fork config attempts-atom]
  (future
    (dotimes [_ (:cycles config)]
      (Thread/sleep (:think-time config))

      (loop []
        (let [acquired?
              (dosync
                (swap! attempts-atom inc)
                (if (and (:free @left-fork) (:free @right-fork))
                  (do
                    (alter left-fork #(-> % (assoc :free false) (update :uses inc)))
                    (alter right-fork #(-> % (assoc :free false) (update :uses inc)))
                    true)
                  false))]
          (when-not acquired?
            (Thread/yield)
            (recur))))

      (Thread/sleep (:eat-time config))

      (dosync
       (alter left-fork assoc :free true)
       (alter right-fork assoc :free true)))))