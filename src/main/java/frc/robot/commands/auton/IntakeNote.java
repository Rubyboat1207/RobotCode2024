package frc.robot.commands.auton;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.RunAnglerCommand;
import frc.robot.commands.RunManipulatorCommand.ManipulatorMode;
import frc.robot.commands.RunAnglerCommand.AnglerModes;
import frc.robot.subsystems.Angler;
import frc.robot.subsystems.Intake;

public class IntakeNote extends SequentialCommandGroup {
    
  public IntakeNote(Intake intake, Angler pivot, DigitalInput bb) {
    addCommands(
      new RunAnglerCommand(AnglerModes.DEPLOY, pivot),
      intake.runMode(ManipulatorMode.INTAKE),
      new RunAnglerCommand(AnglerModes.STOW, pivot)
      
      );
      
  }
}
