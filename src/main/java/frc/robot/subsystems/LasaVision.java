// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.photonvision.EstimatedRobotPose;
import org.photonvision.simulation.VisionSystemSim;
import org.photonvision.targeting.PhotonTrackedTarget;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.wpilibj.Notifier;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.AprilTagCamera;
import frc.robot.Constants;
import frc.robot.ObjectCamera;

public class LasaVision extends SubsystemBase implements AutoCloseable {
  public static class Hardware {
    AprilTagCamera[] cameras;
    ObjectCamera objectCamera;

    public Hardware(ObjectCamera objectCamera, AprilTagCamera... cameras) {
      this.cameras = cameras;
      this.objectCamera = objectCamera;
    }

    public Hardware(AprilTagCamera... cameras) {
      this.cameras = cameras;
    }
  }

  private static LasaVision m_subsystem;

  // private static final String VISIBLE_TAGS_LOG_ENTRY = "/VisibleTags";
  // private static final String ESTIMATED_POSES_LOG_ENTRY = "/EstimatedPoses";
  // private static final String OBJECT_DISTANCE_LOG_ENTRY = "/ObjectDistance";
  // private static final String OBJECT_HEADING_LOG_ENTRY = "/ObjectHeading";
  // private static final String OBJECT_POSE_LOG_ENTRY = "/ObjectPose";

  private AtomicReference<List<EstimatedRobotPose>> m_estimatedRobotPoses;
  private AtomicReference<List<Integer>> m_visibleTagIDs;

  private ObjectCamera m_objectCamera;
  private AprilTagCamera[] m_apriltagCameras;
  private Notifier m_cameraNotifier;
  private AprilTagFieldLayout m_fieldLayout;
  private Supplier<Pose2d> m_poseSupplier;
  private VisionSystemSim m_sim;

  /**
   * Create a new vision subsystem
   * 
   * @param visionHardware Vision hardware
   */
  private LasaVision(Hardware visionHardware) {
    setName(getClass().getSimpleName());
    this.m_apriltagCameras = visionHardware.cameras;
    this.m_objectCamera = visionHardware.objectCamera;
    this.m_estimatedRobotPoses = new AtomicReference<List<EstimatedRobotPose>>(new ArrayList<>());
    this.m_visibleTagIDs = new AtomicReference<List<Integer>>();

    this.m_sim = new VisionSystemSim(getName());

    // Load AprilTag field layout
    m_fieldLayout = AprilTagFields.k2024Crescendo.loadAprilTagLayoutField();
    // PV estimates will always be blue
    m_fieldLayout.setOrigin(AprilTagFieldLayout.OriginPosition.kBlueAllianceWallRightSide);

    // Set field layout for sim
    m_sim.addAprilTags(m_fieldLayout);

    // Setup camera pose estimation threads
    this.m_cameraNotifier = (RobotBase.isReal())
        ? new Notifier(() -> {
          for (var camera : m_apriltagCameras)
            camera.run();
          updateEstimatedGlobalPoses();
        })
        : new Notifier(() -> {
          if (m_poseSupplier != null)
            m_sim.update(m_poseSupplier.get());
          for (var camera : m_apriltagCameras)
            camera.run();
          updateEstimatedGlobalPoses();
        });

    // Set all cameras to primary pipeline
    for (var camera : m_apriltagCameras)
      camera.setPipelineIndex(0);

    // Add object camera to sim
    m_sim.addCamera(m_objectCamera.getCameraSim(), m_objectCamera.getTransform());

    // Add game object target to sim
    m_sim.addVisionTargets(m_objectCamera.getTargetSim());

    // Add AprilTag cameras to sim
    for (var camera : m_apriltagCameras)
      m_sim.addCamera(camera.getCameraSim(), camera.getTransform());

    // Start camera thread
    m_cameraNotifier.setName(getName());
    m_cameraNotifier.startPeriodic(0.2);

    // var tab = Shuffleboard.getTab("Vision");

    // tab.addDoubleArray(
    //     "RobotPose",
    //     () -> pose3dToDoubleArray(new Pose3d(Systems.instance.getDrivebase().getOdometry().getEstimatedPosition())));
    // tab.addDoubleArray(
    //     "Right Cam",
    //     () -> pose3dToDoubleArray(
    //         new Pose3d(Systems.instance.getDrivebase().getOdometry().getEstimatedPosition())
    //             .transformBy(Constants.VisionConstants.SHOOTER_CAMERA_POSE)));
  }

  /**
   * Initialize hardware devices for vision subsystem
   * 
   * @return Hardware object containing all necessary devices for this subsystem
   */
  private static Hardware initializeHardware() {
    Hardware visionHardware = new Hardware(
        new ObjectCamera(
            "driver_camera",
            Constants.VisionConstants.DRIVER_CAMERA_POSE,
            Constants.VisionConstants.DRIVER_CAMERA_RESOLUTION,
            Constants.VisionConstants.DRIVER_CAMERA_FOV),
        new AprilTagCamera(
            "shooter_camera",
            Constants.VisionConstants.SHOOTER_CAMERA_POSE,
            Constants.VisionConstants.ARDUCAM_CAMERA_RESOLUTION,
            Constants.VisionConstants.SHOOTER_CAMERA_FOV),
        new AprilTagCamera(
            "intake_camera",
            Constants.VisionConstants.INTAKE_CAMERA_POSE,
            Constants.VisionConstants.ARDUCAM_CAMERA_RESOLUTION,
            Constants.VisionConstants.SHOOTER_CAMERA_FOV) // temporary, until i can set it to the real value
        );

    return visionHardware;
  }

  /**
   * Update currently estimated robot pose from each camera
   */
  private void updateEstimatedGlobalPoses() {
    List<EstimatedRobotPose> estimatedPoses = new ArrayList<EstimatedRobotPose>();

    List<Integer> visibleTagIDs = new ArrayList<Integer>();
    HashSet<Pose3d> visibleTags = new HashSet<Pose3d>();
    List<Pose2d> loggedPoses = new ArrayList<Pose2d>();
    for (var camera : m_apriltagCameras) {
      var result = camera.getLatestEstimatedPose();
      if (result == null)
        continue;
      result.targetsUsed.forEach((photonTrackedTarget) -> {
        if (photonTrackedTarget.getFiducialId() == -1)
          return;
        visibleTagIDs.add(photonTrackedTarget.getFiducialId());
        visibleTags.add(m_fieldLayout.getTagPose(photonTrackedTarget.getFiducialId()).get());
      });
      estimatedPoses.add(result);
      loggedPoses.add(result.estimatedPose.toPose2d());
    }

    // Log visible tags and estimated poses
    // Logger.recordOutput(getName() + VISIBLE_TAGS_LOG_ENTRY,
    // visibleTags.toArray(new Pose3d[0]));
    // Logger.recordOutput(getName() + ESTIMATED_POSES_LOG_ENTRY,
    // loggedPoses.toArray(new Pose2d[0]));

    m_visibleTagIDs.set(visibleTagIDs);
    m_estimatedRobotPoses.set(estimatedPoses);
  }

  /**
   * Get instance of vision subsystem, creating if nonexistent
   * 
   * @return Instance of vision subsystem
   */
  public static LasaVision getInstance() {
    if (m_subsystem == null)
      m_subsystem = new LasaVision(initializeHardware());
    return m_subsystem;
  }

  /**
   * Set pose supplier for simulation
   * 
   * @param poseSupplier Pose supplier from drive subsystem
   */
  public void setPoseSupplier(Supplier<Pose2d> poseSupplier) {
    m_poseSupplier = poseSupplier;
  }

  public static double[] pose3dToDoubleArray(Pose3d pose) {
    return new double[] {
        pose.getX(),
        pose.getY(),
        pose.getZ(),
        pose.getRotation().getQuaternion().getW(),
        pose.getRotation().getQuaternion().getX(),
        pose.getRotation().getQuaternion().getY(),
        pose.getRotation().getQuaternion().getZ()
    };
  }

  @Override
  public void periodic() {

  }

  @Override
  public void simulationPeriodic() {
    // This method will be called once per scheduler run in simulation
    // var objectLocation = getObjectLocation();
    // if (objectLocation.isEmpty())
    //   return;

    // Logger.recordOutput(getName() + OBJECT_POSE_LOG_ENTRY, objectLocation.get());
  }

  /**
   * Get currently estimated robot poses from each camera
   * 
   * @return List of estimated poses, the timestamp, and targets used to create
   *         the estimate
   */
  public List<EstimatedRobotPose> getEstimatedGlobalPoses() {
    return m_estimatedRobotPoses.getAndSet(Collections.emptyList());
  }

  public double getTargetYaw(int index) {
    return m_apriltagCameras[index].trackedTargetYaw();
  }

  /**
   * Get IDs of currently visible tags
   * 
   * @return List of IDs of currently visible tags
   */
  public List<Integer> getVisibleTagIDs() {
    return m_visibleTagIDs.get();
  }

    /**
     * Get the position of an object that can be seen by the object camera.
     * 
     * @return The position of the object, relative to the field
     */
    // public Optional<Translation2d> getObjectLocation() {
    //   Optional<Measure<Angle>> yaw = m_objectCamera.getYaw();
    //   Optional<Measure<Distance>> distance = m_objectCamera.getDistance();
    //   Pose2d pose = m_poseSupplier.get();
    //   if (yaw.isEmpty() || distance.isEmpty() || pose == null)
    //     return Optional.empty();

    //   // Logger.recordOutput(getName() + OBJECT_DISTANCE_LOG_ENTRY, distance.get());
    //   // Logger.recordOutput(getName() + OBJECT_HEADING_LOG_ENTRY, yaw.get());
    //   return Optional.of(pose.getTranslation().plus(
    //       new Translation2d(
    //           distance.get().in(Units.Meters),
    //           Rotation2d.fromRadians(pose.getRotation().getRadians() + yaw.get().in(Units.Radians)))));
    // }

  @Override
  public void close() {
    for (var camera : m_apriltagCameras)
      camera.close();
 //   m_objectCamera.close();
    m_cameraNotifier.close();
  }
}