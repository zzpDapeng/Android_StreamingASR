package com.mobisys.asr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.HashMap;


public class Dictionary {
    private static final HashMap<String, Integer> word2index = new HashMap<>();
    private static final HashMap<Integer, String> index2word = new HashMap<>();

    public void init(String dictionary_path) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(dictionary_path));
            String str = null;
            while ((str = in.readLine())!= null){
                String[] items = str.split(" ");
                String word = items[0];
                int index = Integer.parseInt(items[1]);
                word2index.put(word,index);
                index2word.put(index, word);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int word_to_index(String word) {
        return word2index.getOrDefault(word, 0);
    }

    public String index_to_word(int index) {
        return index2word.getOrDefault(index, " ");
    }
}