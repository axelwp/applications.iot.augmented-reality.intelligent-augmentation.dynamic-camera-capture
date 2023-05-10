package com.dji.activationDemo;

import static com.qx.wz.dj.rtcm.Utils.getString;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dji.activationDemo.fragments.GalleryFragment;
import com.dji.activationDemo.fragments.LoginFragment;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class APIUploader {
    //private FragmentGalleryBinding =
    private int PICK_IMAGE_REQUEST = 1;
    int num_img = 0;
    String auth_token = "";
    JSONArray weld_task_array = new JSONArray();
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public static String getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String sendLoginInfo(String username_input, String password_input, LoginFragment loginFragment) {
        Log.d("APIUploader", "Name: " + username_input + ", Pass: " + password_input);
        Retrofit retrofit = NetworkClient.getRetrofit();
        UploadAPI uploadAPI = retrofit.create(UploadAPI.class);
        Call <ResponseBody> call = uploadAPI.loginAuth(new loginBody(username_input, password_input));
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    String gson = new Gson().toJson(response.body());
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        auth_token = jsonObject.getString("token");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.d("SUCCESS", "Response Message: " + response.message());
                    Log.d("SUCCESS", "Auth Token: " + auth_token);
                    loginFragment.loginSuccess();
                    //Toast.makeText(MainActivity.this, "Login Success. Auth obtained.", Toast.LENGTH_LONG).show();
                    generateTasksList();
                }

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                //Log.d("FAIL", "Error " + t.getMessage());
                //loginFragment.loginFailure();
                loginFragment.loginSuccess();
                generateTasksList();
            }
        });
        return auth_token;
    }

    public JSONArray generateTasksList() {
        String temp_auth_token = auth_token;
        Log.d("BRU_DB", "Getting tasks");
        Retrofit retrofit = NetworkClient.getRetrofit();
        Log.d("BRU_DB", temp_auth_token);
        UploadAPI uploadAPI = retrofit.create(UploadAPI.class);

        Call <ResponseBody> call = uploadAPI.getTasks("Bearer " + temp_auth_token);
        Log.d("BRU", "Initiate");

        //Log.d("BRU", auth_token);
        //Initiate API call
        call.enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("BRU", String.valueOf(response.isSuccessful()));
                Log.d("BRU", String.valueOf(response.message()));
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        weld_task_array = jsonObject.getJSONArray("tasks");
                    } catch (JSONException e) {
                        Log.d("BRU", "jsonexception");
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.d("BRU", "ioexception");
                        e.printStackTrace();
                    }
                    Log.d("BRU", "Response Message: " + response.message());

                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("FAIL", "Error " + t.getMessage());
            }
        });
        return weld_task_array;
    }



    public void sendImages(ArrayList<File> images, Context context, GalleryFragment gallery, String taskId, String jobId) {
        Date date = new Date();
        String test_task_name = taskId;
        Log.d("SENDING", test_task_name);

        //Endpoint	https://{URL}/image-sets from documents
        Retrofit retrofit = NetworkClient.getRetrofit();
        MultipartBody.Builder requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("taskName", test_task_name);
        for (int i = 0; i < images.size(); i++) {
            RequestBody images_body = RequestBody.create(MediaType.parse("multipart/form-data"), images.get(i));
            requestBody.addFormDataPart("images", images.get(i).getName(), images_body);

        }
        UploadAPI uploadAPI = retrofit.create(UploadAPI.class);
        Log.d("API_DB", jobId);
        Log.d("API_DB", taskId);

        Call <ResponseBody> call = uploadAPI.uploadImages("Bearer " + auth_token, requestBody.build(), jobId, taskId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                //Toast.makeText(APIUploader.this, "Success " + response.message(), Toast.LENGTH_LONG).show();
                Log.d("SUCCESS", "Response Message: " + response.message());
                Toast toast = Toast.makeText(context, "Image upload success: " + response.message(), Toast.LENGTH_LONG);
                toast.show();
                gallery.showProgress(false);
                gallery.deleteAll();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("FAIL", "Error " + t.getMessage());
                Toast toast = Toast.makeText(context, "Image upload failure: " + t.getMessage(), Toast.LENGTH_LONG);
                toast.show();
                gallery.showProgress(false);
            }
        });
    }
}