(ns parallel-filter.core)

(defn p-filter
  [pred chunk-size coll]
  (let [cores (.availableProcessors (Runtime/getRuntime))
        process-block (fn [block] (doall (filter pred block)))]
    (->> coll
         (partition-all chunk-size)
         (partition-all cores)
         (mapcat (fn [window]
                   (let [futures (doall (map #(future (process-block %)) window))]
                     (mapcat deref futures)))))))

(defn demo-performance
  []
  (let [ heavy-pred (fn [x] 
                     (Thread/sleep 10) 
                     (even? x))
        data (range 100)]
    
    (println "Последовательный filter")
    (time (doall (filter heavy-pred data)))
    
    (println "\nПараллельный p-filter")
    (time (doall (p-filter heavy-pred 10 data)))
    nil))