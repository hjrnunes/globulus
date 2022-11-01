(ns globulus.standsim.assortments
  (:require [clojure.java.io :as io]
            [charred.api :as csv]
            [clojure.string :as str]))

(defn generate [{:keys [file
                        pulpwood-price]
                 :or   {pulpwood-price 29}}]
  (let [pad-row (fn [row]
                  (let [padding (- 5 (count row))]
                    (concat row (repeat padding ""))))

        csvdata [["Nr_Assortments:", 2]
                 ["label", "diameter", "lenght", "bark", "value"]
                 ["madeira polpável", 6, 2, 1, pulpwood-price]
                 ["energia", 0, 999, 1, 0]
                 ["Bark:", 0]
                 ["Branches:", 0]
                 ["Top:", 0]
                 ["Topbranches:", 0]
                 ["End of file"]]
        csvdata (->> csvdata
                     (mapv pad-row))]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf
                       :quote \'
                       :quote? (fn [s] (and (not (str/blank? s))
                                            (not (some? (parse-double s)))))))
      csvdata)))