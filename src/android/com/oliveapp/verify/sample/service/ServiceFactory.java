package com.oliveapp.verify.sample.service;

import android.content.Context;

import com.oliveapp.face.liboffline_face_verification.FaceVerifier;

import java.io.IOException;

public class ServiceFactory {
    private static ServiceFactory instance;

    private final FaceVerifier faceVerifier;

    static synchronized void init(Context context) throws IOException {
        if (instance == null) instance = new ServiceFactory(context);
    }

    private ServiceFactory(Context context) throws IOException {
        faceVerifier = new FaceVerifier(context);
    }

    public static ServiceFactory getInstance() {
        return instance;
    }

    public FaceVerifier getFaceVerifier() {
        return faceVerifier;
    }
}
