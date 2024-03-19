// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.mechanisms.swerve.SwerveModule.DriveRequestType;


import com.ctre.phoenix6.mechanisms.swerve.SwerveRequest;
import com.pathplanner.lib.auto.NamedCommands;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.StartEndCommand;
import edu.wpi.first.units.Units;
import frc.robot.Constants.IntakeConstants;
import frc.robot.Constants.TunerConstatns;
import frc.robot.Constants.IntakeConstants.IntakeModes;
import frc.robot.Constants.ShooterConstants.ShooterModes;
import frc.robot.commands.RunAnglerCommand;
import frc.robot.commands.RunClimberCommand;
import frc.robot.commands.RunManipulatorCommand;
import frc.robot.commands.auton.AmpScore;
import frc.robot.commands.auton.DistantSpeakerScore;
import frc.robot.commands.auton.IntakeNote;
import frc.robot.commands.auton.SimpleSpeaker;
import frc.robot.commands.auton.SmartIntakeNote;
import frc.robot.subsystems.Angler;
import frc.robot.subsystems.Climber;
import frc.robot.subsystems.Drivebase;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.LasaVision;
import frc.robot.subsystems.Shooter;
import frc.team5431.titan.core.joysticks.CommandXboxController;

public class RobotContainer {

  public static final CommandXboxController driver = new CommandXboxController(0);
  public static final CommandXboxController operator = new CommandXboxController(1);
  private final Systems systems = new Systems();
  private final Drivebase drivebase = systems.getDrivebase();

  private final Angler pivot = systems.getPivot();
  private final Climber climber = systems.getClimber();

  private final Intake intake = systems.getIntake();
  private final Shooter shooter = systems.getShooter();
  private final AutonMagic autonMagic;

  private SwerveRequest.FieldCentric driveFC = new SwerveRequest.FieldCentric()
      .withDriveRequestType(DriveRequestType.OpenLoopVoltage);

  public RobotContainer() {
    NamedCommands.registerCommand("AmpScore", new AmpScore(intake, pivot));
    NamedCommands.registerCommand("SpeakerScore", new SimpleSpeaker(shooter, intake, pivot));
    NamedCommands.registerCommand("DistantSpeakerScore", new DistantSpeakerScore(shooter, intake, pivot));
    NamedCommands.registerCommand("GrabNote", new IntakeNote(intake, pivot));

    autonMagic = new AutonMagic(systems);

    // drivebase.seedFieldRelative();
    configureBindings();
    DataLogManager.start();
    DriverStation.startDataLog(DataLogManager.getLog());

  }

  private static double deadband(double value, double deadband) {
    if (Math.abs(value) > deadband) {
      if (value > 0.0) {
        return (value - deadband) / (1.0 - deadband);
      } else {
        return (value + deadband) / (1.0 - deadband);
      }
    } else {
      return 0.0;
    }
  }

  private static double modifyAxis(double value) {
    // Deadband
    value = deadband(value, 0.15);

    // More sensitive at smaller speeds
    double newValue = Math.pow(value, 2);

    // Copy the sign to the new value
    newValue = Math.copySign(newValue, value);

    return newValue;
  }

  public void periodic() {

    SmartDashboard.putString("Current Command", CommandScheduler.getInstance().toString());
    if(shooter.mode == ShooterModes.NONE) {
      pivot.setpoint = pivot.setpoint;
    } else if(shooter.mode.usesDistant){
      pivot.setpoint = IntakeConstants.distantStowAngle;
    }  else if(shooter.mode.usesMain) {
      pivot.setpoint = IntakeConstants.anglerConstants.mainAngle;
    }
  }

  Translation2d ellipticalDiscToSquare(double u, double v) {
    double u2 = u * u;
    double v2 = v * v;
    double twosqrt2 = 2.0 * Math.sqrt(2.0);
    double subtermx = 2.0 + u2 - v2;
    double subtermy = 2.0 - u2 + v2;
    double termx1 = subtermx + u * twosqrt2;
    double termx2 = subtermx - u * twosqrt2;
    double termy1 = subtermy + v * twosqrt2;
    double termy2 = subtermy - v * twosqrt2;

    double x = MathUtil.clamp(0.5 * Math.sqrt(termx1) - 0.5 * Math.sqrt(termx2), -1, 1);
    double y = MathUtil.clamp(0.5 * Math.sqrt(termy1) - 0.5 * Math.sqrt(termy2), -1, 1);

    if (Math.abs(x) > 0.9 && Math.abs(y) < 0.2) {
      x = Math.copySign(1, x);
    }

    if (Math.abs(y) > 0.9 && Math.abs(x) < 0.2) {
      y = Math.copySign(1, y);
    }
    return new Translation2d(x, y);
  }

  Translation2d FGSquircularMap(double u, double v) {
    double sgnuv = Math.signum(u * v);
    double sqrt2 = Math.sqrt(2);
    double root = Math
        .sqrt((u * u) + (v * v) - Math.sqrt(((u * u) + (v * v)) * ((u * u) + (v * v) - 4 * (u * u) * (v * v))));

    double x = (sgnuv / (v * sqrt2)) * (root);
    double y = (sgnuv / (u * sqrt2)) * (root);

    if (Math.abs(x) > 0.85 && Math.abs(y) < 0.2) {
      x = Math.copySign(1, x);
    }

    if (Math.abs(y) > 0.85 && Math.abs(x) < 0.2) {
      y = Math.copySign(1, y);
    }

    return new Translation2d(
        x,
        y);
  }

  private void configureBindings() {

    drivebase.setDefaultCommand( // Drivetrain will execute this command periodically
        drivebase.applyRequest(() -> {
          return driveFC
              .withVelocityX(
                  modifyAxis(driver.getLeftY() + (driver.povUp().getAsBoolean() ? 0.1 : 0))
                      * TunerConstatns.kSpeedAt12VoltsMps)
              .withVelocityY(modifyAxis(driver.getLeftX()) * TunerConstatns.kSpeedAt12VoltsMps)
              .withRotationalRate((driver.rightTrigger().getAsBoolean())
                  ? Math.atan2(LasaVision.getInstance().getTargetYaw(0),
                      drivebase.getGyro().getYaw().getValueAsDouble())
                  : -modifyAxis(driver.getRightX()) * TunerConstatns.MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND);
        }));

    driver.y().onTrue(new InstantCommand(() -> drivebase.zeroGyro()));
    driver.leftTrigger().whileTrue(new RunAnglerCommand(RunAnglerCommand.AnglerModes.DEPLOY, pivot));
    driver.a()
        .onTrue(new RunClimberCommand(climber, RunClimberCommand.ClimberMode.EXTENDED));
    driver.rightTrigger().whileTrue(climber.increment(driver.getRightTriggerAxis() * 0.1));
    driver.leftBumper()
        .onTrue(new RunClimberCommand(climber, RunClimberCommand.ClimberMode.RETRACTED));

    // Shooter
    operator.rightTrigger().whileTrue(shooter.runShooterCommand(ShooterModes.SpeakerShot));
    operator.b().whileTrue(shooter.runShooterCommand(ShooterModes.REVERSE));
    operator.a().whileTrue(shooter.runShooterCommand(ShooterModes.SpeakerDistant));
    operator.y().whileTrue(shooter.runShooterCommand(ShooterModes.StageShot));
    operator.start().whileTrue(shooter.runShooterCommand(ShooterModes.AmpShot));

    // Testing
    operator.povDown().onTrue(new SmartIntakeNote(intake, pivot));
    operator.povLeft().onTrue(new IntakeNote(intake, pivot));

    // Intake
    operator.leftTrigger().whileTrue(RunManipulatorCommand.withMode(intake, IntakeModes.INTAKE));
    operator.x().whileTrue(RunManipulatorCommand.withMode(intake, IntakeModes.OUTAKE));
    
    // Intake Angler
    operator.axisGreaterThan(1, 0.15)
        .whileTrue(new RunCommand(() -> pivot.increment(-operator.getLeftY() * 0.1), pivot).repeatedly());
    operator.axisLessThan(1, -0.15)
        .whileTrue(new RunCommand(() -> pivot.increment(-operator.getLeftY() * 0.1), pivot).repeatedly());

    operator.back()
        .onTrue(new InstantCommand(() -> pivot.setRotation(Constants.IntakeConstants.ampAngle), pivot));
    operator.leftBumper().onTrue(new RunAnglerCommand(RunAnglerCommand.AnglerModes.STOW, pivot));
    operator.rightBumper()
        .onTrue((new RunAnglerCommand(RunAnglerCommand.AnglerModes.DEPLOY, pivot)));
    operator.leftStick().onTrue(new RunAnglerCommand(RunAnglerCommand.AnglerModes.DEPLOY, pivot));

  }

  public Command getAutonomousCommand() {
    return autonMagic.procureAuton();
  }

  public void onTeleop() {
    pivot.setpoint = Units.Degree.of(pivot.absoluteEncoder.getPosition());

  }

}