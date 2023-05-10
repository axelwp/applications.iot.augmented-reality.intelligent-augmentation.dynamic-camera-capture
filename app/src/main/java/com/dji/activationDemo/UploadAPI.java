package com.dji.activationDemo;

import java.lang.annotation.Repeatable;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UploadAPI {
//    @Multipart
//    @POST("api/v1/3d-image-service/image-sets")
//    Call<ResponseBody> uploadImages(@Part ("projectName") RequestBody project_name,
//                                    @Part ("taskName") RequestBody taskName,
//                                    @Part ("email") RequestBody email,
//                                    @Part MultipartBody.Part images[]);
    @POST("/api/v1/3d-image-service/weld-tasks/{jobId}/{taskId}")
    Call <ResponseBody> uploadImages(@Header("Authorization") String auth_token , @Body RequestBody body,
                                     @Path("jobId") String jobId, @Path("taskId") String taskId);

    @POST("/api/v1/3d-image-service/users/authenticate")

    Call <ResponseBody> loginAuth(@Body loginBody body);

    @GET("/api/v1/3d-image-service/weld-tasks")
    Call <ResponseBody> getTasks(@Header("Authorization") String auth_token);
}
