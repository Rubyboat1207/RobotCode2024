package frc.robot.subsystems;

import com.revrobotics.CANSparkBase;
import com.revrobotics.CANSparkFlex;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkPIDController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

public class Climber extends SubsystemBase {
    
  private CANSparkFlex left;
  private CANSparkFlex right;
  private RelativeEncoder relativeEncoder;

  public SparkPIDController controller;
  public double setpoint;
  public double downForce = 0.1;
  public ClimberModes mode;

  public enum ClimberModes {
    UP,
    DOWN,
    STOPPED
  }

  public Climber (CANSparkFlex left, CANSparkFlex right) {
    this.left = left;
    this.right = right;
    this.controller = right.getPIDController(); 
    this.controller.setP(0.5);
    this.controller.setI(0);
    this.controller.setD(0.1);
    this.controller.setOutputRange(-0.2, 0.2);

    relativeEncoder = right.getEncoder();

    left.follow(right, true);
    this.left.burnFlash();
    this.right.burnFlash();
    controller.setFeedbackDevice(relativeEncoder);
    this.mode = ClimberModes.STOPPED;
  }

  public void setPosition(double angle) {
    this.setpoint = angle;
    this.mode = (angle <= 0) ? ClimberModes.DOWN : ClimberModes.UP;
  }

  public void incrementPosition(double amt) {
    this.setpoint += amt;
    this.mode = (amt < 0) ? ClimberModes.DOWN : ClimberModes.UP;
  }

  public double getposition() {
    return setpoint;
  }

  @Override public void periodic() {
    SmartDashboard.putNumber("Climber Setpoint", setpoint);
    SmartDashboard.putNumber("Climber Encoder", relativeEncoder.getPosition());
    SmartDashboard.putString("Climber Mode", mode.toString());
   controller.setReference(
      setpoint,
      CANSparkBase.ControlType.kPosition,
      0
    );
  
  }

}
