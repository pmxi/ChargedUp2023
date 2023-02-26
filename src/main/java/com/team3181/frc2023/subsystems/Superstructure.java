package com.team3181.frc2023.subsystems;

import com.team3181.frc2023.Constants.FourBarConstants.ArmPositions;
import com.team3181.frc2023.subsystems.endeffector.EndEffector;
import com.team3181.frc2023.subsystems.fourbar.FourBar;
import com.team3181.frc2023.subsystems.leds.LEDs;
import com.team3181.frc2023.subsystems.leds.LEDs.LEDModes;
import com.team3181.frc2023.subsystems.objectivetracker.ObjectiveTracker;
import com.team3181.frc2023.subsystems.objectivetracker.ObjectiveTracker.NodeLevel;
import com.team3181.frc2023.subsystems.objectivetracker.ObjectiveTracker.Objective;
import com.team3181.frc2023.subsystems.swerve.Swerve;
import com.team3181.lib.controller.BetterXboxController;
import com.team3181.lib.controller.BetterXboxController.Humans;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import org.littletonrobotics.junction.Logger;

public class Superstructure extends SubsystemBase {

    public static class SuperstructurePosition {
        public Rotation2d elbow;
        public Rotation2d shoulder;

        public SuperstructurePosition(Rotation2d elbow, Rotation2d shoulder) {
            this.elbow = elbow;
            this.shoulder = shoulder;
        }
    }

    enum StructureState {
        IDLE, HOME, INTAKE_GROUND, INTAKE_MID, EXHAUST, OBJECTIVE, MANUAL
    }

    enum GamePiece {
        CONE, CUBE, NONE
    }

    private StructureState systemState = StructureState.IDLE;
    private StructureState wantedState = StructureState.IDLE;
    private double sweepGlobal = 0;
    private Objective objective;

    private final static Superstructure INSTANCE = new Superstructure();

    public static Superstructure getInstance() {
        return INSTANCE;
    }

    private Superstructure() {
        objective = ObjectiveTracker.getInstance().getObjective();
    }

    @Override
    public void periodic() {
        boolean shouldAutoRetract = shouldAutoRetract();
        objective = ObjectiveTracker.getInstance().getObjective();
        FourBar fourBar = FourBar.getInstance();
        EndEffector endEffector = EndEffector.getInstance();
        StructureState state = StructureState.IDLE;
        GamePiece gamePiece = GamePiece.NONE;
        double sweepLocal = sweepGlobal;

        // update LEDs
        {
            LEDs leds = LEDs.getInstance();
            if (DriverStation.isDisabled()) {
                leds.setLEDMode(LEDModes.IDLE);
            } else if (DriverStation.isAutonomous()) {
                leds.setLEDMode(LEDModes.RAINBOW);
            } else if (objective.nodeRow == 1 || objective.nodeRow == 4 || objective.nodeRow == 7 || objective.nodeLevel == NodeLevel.HYBRID) {
                if (endEffector.hasPiece()) {
                    leds.setLEDMode(LEDModes.CUBE);
                } else {
                    leds.setLEDMode(LEDModes.FLASH_CUBE);
                }
                gamePiece = GamePiece.CUBE;
            } else if (objective.nodeRow == 0 || objective.nodeRow == 3 || objective.nodeRow == 6) {
                if (endEffector.hasPiece()) {
                    leds.setLEDMode(LEDModes.CONE);
                } else {
                    leds.setLEDMode(LEDModes.FLASH_CONE);
                }
                gamePiece = GamePiece.CONE;
            } else {
                if (endEffector.hasPiece()) {
                    leds.setLEDMode(LEDModes.CONE);
                } else {
                    leds.setLEDMode(LEDModes.FLASH_CONE);
                }
                gamePiece = GamePiece.CONE;
            }
        }

        switch (wantedState) {
            case OBJECTIVE:
                state = StructureState.OBJECTIVE;
                break;
            case INTAKE_GROUND:
                state = StructureState.INTAKE_GROUND;
                break;
            case INTAKE_MID:
                state = StructureState.INTAKE_MID;
                break;
            case HOME:
                state = StructureState.HOME;
                break;
            case EXHAUST:
                state = StructureState.EXHAUST;
                break;
            case MANUAL:
                state = StructureState.MANUAL;
                break;
            case IDLE:
            default:
                state = shouldAutoRetract ? StructureState.HOME : StructureState.IDLE;
                break;
        }
        if (state != systemState) {
            if (systemState == StructureState.OBJECTIVE || systemState == StructureState.INTAKE_GROUND || systemState == StructureState.INTAKE_MID || systemState == StructureState.EXHAUST) {
                endEffector.idle();
            }
            systemState = state;
        }

        switch (systemState) {
            case OBJECTIVE:
                switch (objective.nodeLevel) {
                    case HYBRID:
                        fourBar.setRotations(fourBar.solve(ArmPositions.HYBRID, true));
                        break;
                    case MID:
                        if (gamePiece == GamePiece.CONE) {
                            fourBar.setRotations(fourBar.solve(ArmPositions.MID_CONE, true));
                        }
                        else {
                            fourBar.setRotations(fourBar.solve(ArmPositions.MID_CUBE, true));
                        }
                        break;
                    case HIGH:
                        if (gamePiece == GamePiece.CONE) {
                            fourBar.setRotations(fourBar.solve(ArmPositions.HIGH_CONE, true));
                        }
                        else {
                            fourBar.setRotations(fourBar.solve(ArmPositions.HIGH_CUBE, true));
                        }
                }
                if (shouldAutoScore() && fourBar.atSetpoint()) {
                    endEffector.exhaust();
                }
                else {
//                    TODO: maybe add swerve pathing on the fly
                    endEffector.idle();
                }
                break;
            case INTAKE_GROUND:
                Translation2d pos = new Translation2d(ArmPositions.SWEEP_MIN.getX() + sweepLocal * (ArmPositions.SWEEP_MAX.getX() - ArmPositions.SWEEP_MIN.getX()), ArmPositions.SWEEP_MIN.getY());
                fourBar.setRotations(FourBar.getInstance().solve(pos, true));
                endEffector.intake();
                break;
            case INTAKE_MID:
                fourBar.setRotations(FourBar.getInstance().solve(ArmPositions.MID_INTAKE, true));
                endEffector.intake();
            case EXHAUST:
                fourBar.hold();
                endEffector.exhaust();
                break;
            case IDLE:
                fourBar.hold();
                endEffector.idle();
                break;
            case MANUAL:
                fourBar.setArmVoltage(0, -3 * BetterXboxController.getController(Humans.OPERATOR).getLeftY());
                fourBar.setArmVoltage(1, -3 * BetterXboxController.getController(Humans.OPERATOR).getRightY());
                endEffector.idle();
                break;
            case HOME:
            default:
                fourBar.setRotations(new Rotation2d[] {ArmPositions.STORAGE_SHOULDER, ArmPositions.STORAGE_ELBOW});
                endEffector.idle();
                break;
        }

        Logger.getInstance().recordOutput("Superstructure/Wanted State", wantedState.toString());
        Logger.getInstance().recordOutput("Superstructure/System State", systemState.toString());
    }

    public void idle() {
        wantedState = StructureState.IDLE;
    }

    public void manual() {
        wantedState = StructureState.MANUAL;
    }

    public void exhaust() {
        wantedState = StructureState.EXHAUST;
    }

    public void objective() {
        wantedState = StructureState.OBJECTIVE;
    }

    public void collectGround() {
        sweepGlobal = 0;
        wantedState = StructureState.INTAKE_GROUND;
    }

    /**
     *
     * @param sweep from 0-1
     */
    public void collectGround(double sweep) {
        sweepGlobal = sweep;
        wantedState = StructureState.INTAKE_GROUND;
    }

    public void collectMid() {
        wantedState = StructureState.INTAKE_MID;
    }

    public void home() {
        wantedState = StructureState.HOME;
    }

    private boolean shouldAutoRetract() {
//        check if in center of field
        return Swerve.getInstance().getPose().getX() > 5.3 && Swerve.getInstance().getPose().getX() < 11.25;
    }

    private boolean shouldAutoScore() {
//        check if at correct pose for current node
        return false;
    }
}