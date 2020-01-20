#!/bin/bash

# Extracts the titles from the first k XML documents in a directory and stores them in titles.txt

touch titles.txt
for filename in $(ls processed | head -10000)
do
    line=$(sed -n "3{p;q;}" "processed/$filename")
    size=${#line}-16
    echo ${line:7:$size} >> titles.txt
done
