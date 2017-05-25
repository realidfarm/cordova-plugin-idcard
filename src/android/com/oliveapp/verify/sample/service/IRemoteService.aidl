// IRemoteService.aidl
package com.oliveapp.verify.sample.service;

// Declare any non-default types here with import statements
import com.oliveapp.verify.sample.model.FaceVerifyResult;

interface IRemoteService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
     byte[] extractFeature(in byte[] picByte, int picType);

     byte[] extractFeatureFromPackage(in byte[] packByte, int index, int picType);

     FaceVerifyResult verifyFeature(in byte[] feature1, in byte[] feature2);

     byte[] getFrameFromPackageData(in byte[] packageData);

     boolean isInitialized();
}
