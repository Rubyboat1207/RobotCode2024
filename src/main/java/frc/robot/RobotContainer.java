// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import frc.robot.Constants.OperatorConstants;
import frc.robot.commands.RunIntakeCommand;
import frc.robot.subsystems.Intake;
import edu.wpi.first.wpilibj2.command.Command;
import frc.team5431.titan.core.joysticks.CommandXboxController;

public class RobotContainer {

  private final Systems systems = new Systems();
  private final CommandXboxController m_driverController =
      new CommandXboxController(OperatorConstants.kDriverControllerPort);
  private final CommandXboxController driver = new CommandXboxController(0);
  private final CommandXboxController operator = new CommandXboxController(1);

  private void configureBindings() {
    operator.a().whileTrue(new RunIntakeCommand(true, systems.getIntake())); //remember to replace null
    operator.b().whileTrue(new RunIntakeCommand(false, systems.getIntake())); //remember to replace null
  }

  public RobotContainer() {

  }

  public Command getAutonomousCommand() {
    return null;
  }
}
