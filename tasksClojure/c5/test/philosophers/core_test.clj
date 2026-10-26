(ns philosophers.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [philosophers.core :refer [create-forks philosopher]]))

(defn run-experiment
  [n think-time eat-time cycles]
  (let [forks (create-forks n)
        attempts (atom 0)
        config {:cycles cycles :think-time think-time :eat-time eat-time}
        start-time (System/currentTimeMillis)
        
        phils (doall
               (for [i (range n)]
                 (let [left (forks i)
                       right (forks (mod (inc i) n))]
                   (philosopher i left right config attempts))))]
    
    (doseq [p phils] @p)
    
    (let [total-time (- (System/currentTimeMillis) start-time)
          total-success (* n cycles)
          restarts (- @attempts total-success)
          total-fork-uses (reduce + (map #(:uses @%) forks))]
      
      (println (format "\nСценарий: %d философов | %d мс размышления | %d мс еды | %d циклов" 
                       n think-time eat-time cycles))
      (println (format "Время выполнения: %d мс" total-time))
      (println (format "Попыток транзакций: %d (Рестартов/повторов: %d)" @attempts restarts))
      
      {:restarts restarts
       :total-fork-uses total-fork-uses
       :expected-fork-uses (* n cycles 2)})))

(deftest philosophers-stm-test
  (testing "Эксперимент 1: Четное количество философов (4)"
    (let [stats (run-experiment 4 10 10 20)]
      (is (= (:expected-fork-uses stats) (:total-fork-uses stats)))))

  (testing "Эксперимент 2: Нечетное количество философов (5)"
    (let [stats (run-experiment 5 10 10 20)]
      (is (= (:expected-fork-uses stats) (:total-fork-uses stats)))))

  (testing "Эксперимент 3: Сценарий Live-lock (высокая конкуренция)"
    (let [stats (run-experiment 8 0 1 50)]
      (is (= (:expected-fork-uses stats) (:total-fork-uses stats)))
      (is (> (:restarts stats) 0)))))