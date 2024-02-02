package frc.robot;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import frc.robot.Constants.ApriltagConstants.zone;

public class TypedApriltag {

  public final int id;

  public TypedApriltag(int id) {
    this.id = id;
  }

  /**
   * @param robotTeam
   * @param windowName
   * @return
   */
  public boolean isEnemyApriltag() {
    return !isFriendlyApriltag();
  }

  /**
   * @returns true if apriltag id is included in your alliance section
   */
  public boolean isFriendlyApriltag() {
    boolean isBlue = Constants.ApriltagConstants.blue.contains(id);
    var robotTeam = DriverStation.getAlliance();
    if (robotTeam.isPresent()) {
      if (robotTeam.get() == Alliance.Red) {
        return !isBlue;
      }
      if (robotTeam.get() == Alliance.Blue) {
        return isBlue;
      } else {
        System.out.println("Error in getting alliance, none returned, frc/robot/TypedApriltag.java");
        return false;
      }
    }
    System.out.println("Error in getting alliance, robotTeam is not present, frc/robot/TypedApriltag.java");
    return false;
  }

  public zone zoneMatch() {
    if (isSpeaker()) {
      return zone.SPEAKER;
    } else if (isAmp()) {
      return zone.AMP;
    } else if (isStage()) {
      return zone.STAGE;
    } else if (isSource()) {
      return zone.SOURCE;
    }
    return null;
  }

  public boolean isSource() {
    return Constants.ApriltagConstants.source.contains(id);
  }

  public boolean isAmp() {
    return Constants.ApriltagConstants.amp.contains(id);
  }

  public boolean isSpeaker() {
    return Constants.ApriltagConstants.speaker.contains(id);
  }

  public boolean isStage() {
    return Constants.ApriltagConstants.stage.contains(id);
  }
}
//lune est "cool <-(cool in french for those who dont speak french)"