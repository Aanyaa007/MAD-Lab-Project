package com.senseshield.services;


import android.media.MediaRecorder;
import android.util.Log;

public class NoiseMonitor {

    private MediaRecorder recorder;

    public void start() {
        Log.d("NOISE_DEBUG", "Recorder started");
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile("/data/data/com.senseshield/temp.3gp");

            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            recorder = null;
        }
    }

    public double getDb() {
        try {
            if (recorder != null) {
                int amplitude = recorder.getMaxAmplitude();
                if (amplitude > 0) {
                    return 20 * Math.log10(amplitude);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void stop() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception e) {
                e.printStackTrace(); // ignore crash
            }
            try {
                recorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }
}
