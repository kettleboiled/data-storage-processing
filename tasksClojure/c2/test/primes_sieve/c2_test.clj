(ns primes-sieve.c2-test
  (:require [clojure.test :refer [deftest is]]
            [primes-sieve.c2 :refer [infinite-primes]]))

(deftest infinite-primes-test
  (clojure.test/testing "Проверка первых 10 простых чисел"
    (is (= '(2 3 5 7 11 13 17 19 23 29) 
           (take 10 infinite-primes))))

  (clojure.test/testing "Проверка 100-го простого числа (индекс 99)"
    (is (= 541 (nth infinite-primes 99))))

  (clojure.test/testing "Проверка 1000-го простого числа (индекс 999)"
    (is (= 7919 (nth infinite-primes 999)))))