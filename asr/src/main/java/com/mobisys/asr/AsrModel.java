package com.mobisys.asr;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AsrModel {
    private static Module encoder = null;
    private static Module decoder = null;
    private static Module joint = null;
    private static Dictionary dictionary = new Dictionary();
    private static int MAX_AUDIO_FEATURE_LEN = 410;
    private static int MAX_LABEL_LEN = 40;

    public void initModel(Context context) {

        try {
            System.out.println("加载encoder");
            encoder = Module.load(Utils.assetFilePath(context, "encoder.pt"));
            System.out.println("加载decoder");
            decoder = Module.load(Utils.assetFilePath(context, "decoder.pt"));
            System.out.println("加载joint");
            joint = Module.load(Utils.assetFilePath(context, "joint.pt"));
            System.out.println("初始化字典");
            dictionary.init(Utils.assetFilePath(context, "grapheme_table.txt"));
        } catch (IOException e) {
            Log.e("ASR", "Error reading assets", e);
            e.printStackTrace();
        }
    }

    /**
     * 识别音频文件
     *
     * @param filePath
     * @return
     */
    public String recognize(String filePath) {
        String str = null;
        float[][] audio_feature = AudioProcess.get_feature(filePath);
        if (audio_feature.length > MAX_AUDIO_FEATURE_LEN) {
            StringBuilder stringBuilder = new StringBuilder();
            float[][][] audio_features = clip_feature(audio_feature);
            for (float[][] audioFeature : audio_features) {
                stringBuilder.append(recognize_unit(audioFeature));
            }
            str = stringBuilder.toString();
        } else {
            str = recognize_unit(audio_feature);
        }
        return str;
    }

    /**
     * 识别short数组的大段音频文件
     *
     * @param audioData
     * @return
     */
    public String recognize(short[] audioData) {
        String str = null;
        float[][] audio_feature = AudioProcess.get_feature(audioData);
        if (audio_feature.length > MAX_AUDIO_FEATURE_LEN) {
            StringBuilder stringBuilder = new StringBuilder();
            float[][][] audio_features = clip_feature(audio_feature);
            for (float[][] audioFeature : audio_features) {
                stringBuilder.append(recognize_unit(audioFeature));
            }
            str = stringBuilder.toString();
        } else {
            str = recognize_unit(audio_feature);
        }
        return str;
    }

    /**
     * 识别模块，调用之前保证特征长度不会超过最大长度
     *
     * @param audio_feature
     * @return
     */
    public String recognize_unit(float[][] audio_feature) {
        int time_steps = audio_feature.length;
        int feature_dim = audio_feature[0].length;
        long[] audio_shape = new long[]{1, time_steps, feature_dim};
        final Tensor audio_tensor = Tensor.fromBlob(flatten(audio_feature), audio_shape);

        long[] mask_shape = new long[]{time_steps, time_steps, 1};
        final Tensor audio_mask = Tensor.fromBlob(flatten(context_mask(time_steps, 10, 2)), mask_shape);

        Tensor encoder_output = encoder.forward(IValue.from(audio_tensor), IValue.from(audio_mask)).toTensor();

        float[] encoder_output_list = encoder_output.getDataAsFloatArray();
        float[][] encoder_output_matrix = reshape(encoder_output_list, time_steps, feature_dim);

        ArrayList<Long> token_array = new ArrayList<>();
        token_array.add((long) 0);

        HashMap<Integer, Tensor> tensorMap = new HashMap<>();  // 记录label_encoder的输出，避免重复运算

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < time_steps; i++) {
            // label -> decoder -> label_output
            Tensor label_tensor = null;
            if ((label_tensor = tensorMap.get(token_array.size())) == null) {
                long[] token_shape = new long[]{1, token_array.size()};
                long[] token = arrayToListLong(token_array);
                final Tensor token_tensor = Tensor.fromBlob(token, token_shape);
                Tensor label_output = decoder.forward(IValue.from(token_tensor)).toTensor();
                float[] label_output_list = label_output.getDataAsFloatArray();
                float[][] label_output_matrix = reshape(label_output_list, token.length, feature_dim);
                label_tensor = Tensor.fromBlob(label_output_matrix[label_output_matrix.length - 1], new long[]{feature_dim});  // 仅取最后一帧
                tensorMap.put(token_array.size(), label_tensor);
            }
            Tensor frame_tensor = Tensor.fromBlob(encoder_output_matrix[i], new long[]{feature_dim});

            Tensor joint_output = joint.forward(IValue.from(frame_tensor), IValue.from(label_tensor)).toTensor();
            float[] joint_output_list = joint_output.getDataAsFloatArray();
            int max_index = argmax(joint_output_list);
            if (max_index != 0) {
                String word = dictionary.index_to_word(max_index);
                stringBuilder.append(word);
                token_array.add((long) max_index);
                // 如果标签数量太多，则丢弃最前面的标签
                if (token_array.size() > MAX_LABEL_LEN) {
                    token_array.remove(0);
                }
                i += 2;  //跳音频帧，因为预测帧之后往往都是多个空白帧
            }
        }
        return stringBuilder.toString();

    }


    /**
     * 当特征长度超过最大长度时，对特征（二维）进行切分，得到三维特征
     *
     * @param audio_feature ：
     * @return ：
     */
    public static float[][][] clip_feature(float[][] audio_feature) {
        int feature_dim = audio_feature[0].length;
        int clip_num = (int) Math.ceil(audio_feature.length / (float) MAX_AUDIO_FEATURE_LEN);
        float[][][] temp = new float[clip_num][MAX_AUDIO_FEATURE_LEN][feature_dim];
        for (int i = 0; i < clip_num - 1; i++) {
            System.arraycopy(audio_feature, i * MAX_AUDIO_FEATURE_LEN, temp[i], 0, MAX_AUDIO_FEATURE_LEN);
        }
        System.arraycopy(audio_feature, MAX_AUDIO_FEATURE_LEN * (clip_num - 1), temp[clip_num - 1], 0, audio_feature.length % MAX_AUDIO_FEATURE_LEN);
        return temp;
    }

    /**
     * ArrayList<Long>转为long[]数组, 用于解码的token
     *
     * @param arrayList ：
     * @return ：
     */
    public static long[] arrayToListLong(List<Long> arrayList) {
        long[] list = new long[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            list[i] = arrayList.get(i);
        }
        return list;
    }

    /**
     * @param arrayList ：
     * @return ：
     */
    public static short[] arrayToListShort(List<Short> arrayList) {
        System.out.println(arrayList.size());
        short[] list = new short[arrayList.size()];
        System.out.println(list.length);
        for (int i = 0; i < list.length; i++) {
            list[i] = arrayList.get(i);
        }
        return list;
    }


    public static short[] clipArrayToListShort(List<Short> arrayList, long start, long end) {
        int len = (int) (end - start);
        short[] list = new short[len];
        for (int i = 0; i < len; i++) {
            list[i] = arrayList.get(i+(int)start);
        }
        return list;
    }

    /**
     * 寻找最大值的索引
     *
     * @param data：一维数组
     * @return ： 最大值索引
     */
    public static int argmax(float[] data) {
        int index = 0;
        float max = data[0];
        for (int i = 0; i < data.length; i++) {
            if (data[i] >= max) {
                max = data[i];
                index = i;
            }
        }
        return index;
    }

    /**
     * 将一维数组转换为二维数组
     *
     * @param data ： 一维数组
     * @param m    ： 行
     * @param n    ： 列
     * @return ： 二维数组
     */
    public static float[][] reshape(float[] data, int m, int n) {
        float[][] temp = new float[m][n];
        for (int i = 0; i < m; i++) {
            System.arraycopy(data, i * n, temp[i], 0, n);
        }
        return temp;
    }

    /**
     * 音频上下文遮掩
     *
     * @param seq_len ：
     * @param left    ：
     * @param right   ：
     * @return ：
     */
    public static float[][][] context_mask(int seq_len, int left, int right) {
        float[][][] mask = new float[seq_len][seq_len][1];
        for (int i = 0; i < seq_len; i++) {
            for (int j = 0; j < seq_len; j++) {
                if (i - left <= j && j <= i + right) {
                    mask[i][j][0] = 0;
                } else {
                    mask[i][j][0] = 1;
                }
            }
        }
        return mask;
    }

    /**
     * 将二维数组展开为一维数组
     *
     * @param data : 二维数组
     * @return ： 一维数组
     */
    public static float[] flatten(float[][] data) {
        int time_steps = data.length;
        int feature_dims = data[0].length;
        float[] temp = new float[time_steps * feature_dims];
        for (int i = 0; i < time_steps; i++) {
            System.arraycopy(data[i], 0, temp, i * feature_dims, feature_dims);
        }
        return temp;
    }

    /**
     * 将三维数组展开为一维数组
     *
     * @param data : 三维mask数组
     * @return ： 一维数组
     */
    public static float[] flatten(float[][][] data) {
        int dim_1 = data.length;
        int dim_2 = data[0].length;
        int dim_3 = data[0][0].length;
        float[] temp = new float[dim_1 * dim_2 * dim_3];
        for (int i = 0; i < dim_1; i++) {
            for (int j = 0; j < dim_2; j++) {
                for (int k = 0; k < dim_3; k++) {
                    temp[i * dim_2 * dim_3 + j * dim_3 + k] = data[i][j][k];
                }
            }
        }
        return temp;
    }

}
