package com.dji.activationDemo;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
public class NetworkClient {
    private static Retrofit retrofit;
    private static String BASE_URL = "https://inference-tt-integ.i2a.dev";

    public static Retrofit getRetrofit() {
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
        if (retrofit == null) {
            retrofit = new Retrofit.Builder().baseUrl(BASE_URL).
                    addConverterFactory(GsonConverterFactory.create()).client(okHttpClient).build();

        }
        return retrofit;
    }
}
