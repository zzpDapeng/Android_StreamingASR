package com.mobisys.asr;

import be.tarsos.dsp.util.fft.FloatFFT;

public class Melspectrogram {

    public static float[][] mel(int sr, int n_fft, int n_mels) {
        float fmin = 0;
        float fmax = (float) sr / 2;
        boolean htk = false;
        float[][] weights = new float[n_mels][(int) (1 + n_fft / 2)];
        float[] fftfreqs = fft_frequencies(sr, n_fft);
        float[] mel_f = mel_frequencies(n_mels + 2, fmin, fmax, htk);
        float[] fdiff = diff(mel_f);
        float[][] ramps = subtract_outer(mel_f, fftfreqs);
        for (int i = 0; i < n_mels; i++) {
            for (int j = 0; j < ramps[0].length; j++) {
                double lower = -ramps[i][j] / fdiff[i];
                double upper = ramps[i + 2][j] / fdiff[i + 1];
                weights[i][j] = (float) (Math.max(0, Math.min(lower, upper)));
            }
        }
        double[] enorm = new double[n_mels];
        for (int i = 0; i < enorm.length; i++)
            enorm[i] = (2.0 / (mel_f[i + 2] - mel_f[i]));
        for (int i = 0; i < n_mels; i++) {
            for (int j = 0; j < weights[0].length; j++) {
                weights[i][j] = (float) (weights[i][j] * enorm[i]);
            }
        }
        return weights;

    }

    private static float[] mel_frequencies(int n_mels, float fmin, float fmax, boolean htk) {
        float min_mel = hz_to_mel(fmin, false);
        float max_mel = hz_to_mel(fmax, false);
        float[] mels = linespace(min_mel, max_mel, n_mels);
        return mel_to_hz(mels, htk);
    }

    private static float[] fft_frequencies(int sr, int n_fft) {
        return linespace(0, (float) sr / 2, (int) (1 + n_fft / 2));
    }

    private static float[] linespace(float start, float end, int num) {
        float[] result = new float[num];
        float q = (end - start) / (num - 1);
        for (int i = 0; i < num; i++)
            result[i] = start + i * q;
        return result;
    }

    private static float hz_to_mel(float frequencies, boolean htk) {
        if (htk)
            return (float) (2595.0 * Math.log10(1.0 + frequencies / 700.0));
        double f_min = 0.0;
        double f_sp = 200.0 / 3;
        double mels = (frequencies - f_min) / f_sp;
        double min_log_hz = 1000.0;
        double min_log_mel = (min_log_hz - f_min) / f_sp;
        double logstep = Math.log(6.4) / 27.0;
        if (frequencies >= min_log_hz)
            mels = min_log_mel + Math.log(frequencies / min_log_hz) / logstep;
        return (float) mels;
    }

    private static float[] mel_to_hz(float[] mels, boolean htk) {
        double f_min = 0.0;
        double f_sp = 200.0 / 3;
        float[] freqs = new float[mels.length];
        for (int i = 0; i < mels.length; i++)
            freqs[i] = (float) (f_min + f_sp * mels[i]);
        double min_log_hz = 1000.0;
        double min_log_mel = (min_log_hz - f_min) / f_sp;
        double logstep = Math.log(6.4) / 27.0;
        for (int i = 0; i < mels.length; i++) {
            if (mels[i] >= min_log_mel)
                freqs[i] = (float) (min_log_hz * Math.exp(logstep * (mels[i] - min_log_mel)));
        }
        return freqs;
    }

    private static float[] diff(float[] data) {
        float[] result = new float[data.length - 1];
        for (int i = 1; i < data.length; i++)
            result[i - 1] = data[i] - data[i - 1];
        return result;
    }

    private static float[][] subtract_outer(float[] dataA, float[] dataB) {
        float[][] result = new float[dataA.length][dataB.length];
        for (int i = 0; i < dataA.length; i++) {
            for (int j = 0; j < dataB.length; j++) {
                result[i][j] = dataA[i] - dataB[j];
            }
        }
        return result;
    }

    public static float[][] stft(float[] y, int n_fft, int hop_length, int win_length) {
        HannPyWindow hannPyWindow = new HannPyWindow();
        float[] fft_window = hannPyWindow.generateCurve(win_length);
        fft_window = pad_center(fft_window, n_fft);
        System.out.println(fft_window.length);
        float[] y_pad = pad(y, n_fft / 2, "reflect");
        float[][] y_frames = frame(y_pad, n_fft, hop_length);
        float[] temp = new float[2 * n_fft];
        FloatFFT fft = new FloatFFT(n_fft);
//        FFT fft = new FFT(n_fft, new HannPyWindow());
        float[][] stft_matrix = new float[y_frames.length][(int) 1 + n_fft / 2];

        for (int i = 0; i < y_frames.length; i++) {
            for (int j = 0; j < y_frames[0].length; j++) {
                temp[j] = fft_window[j] * y_frames[i][j];
            }
//            fft.realForward(temp);
            fft.realForwardFull(temp);
//            fft.forwardTransform(temp);
            float modulus = 0;
            for (int k = 0; k < (1 + n_fft / 2); k++) {
                int realIndex = 2 * k;
                int imgIndex = 2 * k + 1;
                if (realIndex == n_fft)
                    modulus = temp[realIndex] * temp[realIndex];
                else
                    modulus = temp[realIndex] * temp[realIndex] + temp[imgIndex] * temp[imgIndex];
                stft_matrix[i][k] = modulus;
            }
        }
        return stft_matrix;
    }

    public static float[] pad_center(float[] data, int size) {
        int n = data.length;
        int lpad = (size - n) / 2;
        return pad(data, lpad, "constant");
    }


    public static float[][] frame(float[] y, int frame_length, int hop_length) {
        int n_frames = 1 + (y.length - frame_length) / hop_length;
        float[][] result = new float[n_frames][frame_length];
        for (int i = 0; i < n_frames; i++) {
            for (int j = 0; j < frame_length; j++) {
                result[i][j] = y[i * hop_length + j];
            }
        }
        return result;
    }


    public static float[] pad(float[] y, int n, String mode) {
        float[] temp = null;
        switch (mode) {
            case "reflect": {
                temp = new float[y.length + 2 * n];
                for (int i = 0; i < y.length; i++) {
                    int dis1 = n - i;
                    int dis2 = n - (y.length - i - 1);
                    temp[i + n] = y[i];
                    if (dis1 >= 0)
                        temp[dis1] = y[i];
                    if (dis2 >= 0)
                        temp[temp.length - dis2 - 1] = y[i];
                }
                break;
            }
            case "constant": {
                temp = new float[y.length + 2 * n];
                for (int i = 0; i < temp.length; i++) {
                    if (n <= i && i < y.length + n)
                        temp[i] = y[i - n];
                    else
                        temp[i] = 0;
                }
                break;
            }
        }
        return temp;
    }

    public static float[][] melspectrogram(float[] y, int sr, int n_fft, int hop_length, int win_length, int n_mels) {
        float[][] S = stft(y, n_fft, hop_length, win_length);
        float[][] mel_basis = mel(sr, n_fft, n_mels);
        float[][] result = new float[mel_basis.length][S.length];
        for (int i = 0; i < mel_basis.length; i++) {
            for (int j = 0; j < S.length; j++) {
                double sum = 0;
                for (int k = 0; k < S[0].length; k++) {
                    sum += mel_basis[i][k] * S[j][k];
                }
                result[i][j] = (float) sum;
            }
        }
        return result;
    }

    public static float[][] amplitude_to_db(float[][] S) {
        float[][] data = new float[S.length][S[0].length];
        float ref = 1.0f;
        float amin = 1E-5f;
        float top_db = 80.0f;
        for (int i = 0; i < S.length; i++) {
            for (int j = 0; j < S[i].length; j++) {
                data[i][j] = Math.abs(S[i][j]);
            }
        }
        float ref_value = Math.abs(ref);
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                data[i][j] = data[i][j] * data[i][j];
            }
        }
        return power_to_db(data, ref_value * ref_value, amin * amin, top_db);
    }

    public static float[][] power_to_db(float[][] S, float ref, float amin, float top_db) {
        float ref_value = Math.abs(ref);
        float[][] log_spec = new float[S.length][S[0].length];
        for (int i = 0; i < S.length; i++) {
            for (int j = 0; j < S[i].length; j++) {
                log_spec[i][j] = (float) (10.0 * Math.log10(Math.max(amin, S[i][j])));
                log_spec[i][j] -= (float) (10.0 * Math.log10(Math.max(amin, ref_value)));
            }
        }
        float max = max(log_spec);
        for (int i = 0; i < S.length; i++) {
            for (int j = 0; j < S[i].length; j++) {
                log_spec[i][j] = Math.max(log_spec[i][j], max - top_db);
            }
        }
        return log_spec;
    }

    public static float max(float[][] data) {
        float max = data[0][0];
        for (float[] datum : data) {
            for (float v : datum) {
                if (v > max) {
                    max = v;
                }
            }
        }
        return max;
    }
}
