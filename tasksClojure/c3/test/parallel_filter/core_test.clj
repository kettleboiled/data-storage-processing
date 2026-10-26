(ns parallel-filter.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [parallel-filter.core :refer [p-filter]]))

(deftest p-filter-test
  (testing "Фильтрация конечной последовательности"
    (is (= '(2 4 6 8 10) 
           (p-filter even? 3 (range 1 12)))))
  
  (testing "Бесконечная последовательность и ленивость"
    (is (= '(0 2 4 6 8) 
           (take 5 (p-filter even? 10 (range))))))
  
  (testing "Пустая коллекция"
    (is (= '() 
           (p-filter even? 5 []))))
  
  (testing "Отсутствие подходящих элементов (все отфильтровано)"
    (is (= '() 
           (p-filter string? 5 (range 50))))))