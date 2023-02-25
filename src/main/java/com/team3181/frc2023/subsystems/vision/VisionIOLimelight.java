package com.team3181.frc2023.subsystems.vision;
import com.team3181.lib.util.LimelightHelpers;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import org.littletonrobotics.junction.Logger;

public class VisionIOLimelight implements VisionIO {
    public Pose3d lastPose = new Pose3d();

    private final NetworkTable limelight = LimelightHelpers.getLimelightNTTable("");;

    public VisionIOLimelight() {
//        setLEDs(LED.OFF);
    }

    public void updateInputs(VisionIOInputs inputs) {
        NetworkTableEntry heartbeatEntry = limelight.getEntry("hb");
        NetworkTableEntry botposeEntry = limelight.getEntry("botpose_wpiblue");

        double pipelineLatency = LimelightHelpers.getLatency_Pipeline("");
        double captureLatency = LimelightHelpers.getLatency_Capture("");
        double totalLatency = pipelineLatency + captureLatency; // ms

        if (!lastPose.equals(new Pose3d(botposeEntry.getDoubleArray(new double[7])[0], botposeEntry.getDoubleArray(new double[7])[1], botposeEntry.getDoubleArray(new double[7])[2], new Rotation3d()))) {
            inputs.captureTimestamp = (Logger.getInstance().getRealTimestamp() / 1000000.0) - Units.millisecondsToSeconds(totalLatency);
            inputs.botXYZ = new double[]{botposeEntry.getDoubleArray(new double[7])[0], botposeEntry.getDoubleArray(new double[7])[1], botposeEntry.getDoubleArray(new double[7])[2]};
            inputs.botYPR = new double[]{botposeEntry.getDoubleArray(new double[7])[3], botposeEntry.getDoubleArray(new double[7])[4], botposeEntry.getDoubleArray(new double[7])[5]};
            lastPose = new Pose3d(botposeEntry.getDoubleArray(new double[7])[0], botposeEntry.getDoubleArray(new double[7])[1], botposeEntry.getDoubleArray(new double[7])[2], new Rotation3d());
        }
        inputs.captureLatency = captureLatency;
        inputs.pipelineLatency = pipelineLatency;
        inputs.hasTarget = LimelightHelpers.getTV("");
        inputs.connected = heartbeatEntry.getDouble(0.0) > 0.0;
        inputs.vAngle = LimelightHelpers.getTY("");
        inputs.hAngle = LimelightHelpers.getTX("");
    }

    @Override
    public void setPipeline(Pipelines pipeline) {
        limelight.getEntry("pipeline").setDouble(pipeline.getNum());
    }

    @Override
    public void setCameraModes(CameraMode camera) {
        limelight.getEntry("camMode").setDouble(camera.getNum());
    }

    @Override
    public void setLEDs(LED led) {
        limelight.getEntry("ledMode").setDouble(led.getNum());
    }
}