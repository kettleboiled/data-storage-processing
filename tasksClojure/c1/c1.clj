(defn generate-strings [alphabet n]
  (if (<= n 0)
    '()
    (reduce (fn [acc _]
              (reduce concat '()
                      (map (fn [s]
                             (map (fn [c] (str s c))
                                  (remove (fn [c] (.endsWith s c)) alphabet)))
                           acc)))
            alphabet
            (repeat (dec n) nil))))