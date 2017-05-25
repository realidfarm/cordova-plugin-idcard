package com.oliveapp.verify.sample.model;

import android.os.Parcel;
import android.os.Parcelable;

// 封装结果类，实现Parcelable接口

public class FaceVerifyResult implements Parcelable {

    private boolean isSamePerson;
    private double similarity;

    public FaceVerifyResult(boolean isSamePerson, double similarity) {
        this.isSamePerson = isSamePerson;
        this.similarity = similarity;
    }

    protected FaceVerifyResult(Parcel in) {
        isSamePerson = in.readByte() != 0;
        similarity = in.readDouble();
    }

    public static final Creator<FaceVerifyResult> CREATOR = new Creator<FaceVerifyResult>() {
        @Override
        public FaceVerifyResult createFromParcel(Parcel in) {
            return new FaceVerifyResult(in);
        }

        @Override
        public FaceVerifyResult[] newArray(int size) {
            return new FaceVerifyResult[size];
        }
    };

    public boolean isSamePerson() {
        return isSamePerson;
    }

    public void setSamePerson(boolean samePerson) {
        isSamePerson = samePerson;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isSamePerson ? 1 : 0));
        dest.writeDouble(similarity);
    }
}
