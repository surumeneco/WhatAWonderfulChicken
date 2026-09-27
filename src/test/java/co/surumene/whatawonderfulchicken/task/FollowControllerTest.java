package co.surumene.whatawonderfulchicken.task;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowControllerTest {
    @Test
    void approachesToFiveBlocksInFrontOfFollowTarget() {
        Vector destination = FollowController.followDestination(
                new Vector(0.0, 64.0, 0.0), new Vector(20.0, 70.0, 0.0));

        assertEquals(15.0, destination.getX(), 1.0e-9);
        assertEquals(70.0, destination.getY(), 1.0e-9);
        assertEquals(0.0, destination.getZ(), 1.0e-9);
    }

    @Test
    void approachesFromEitherDirectionWithoutPassingThroughTarget() {
        Vector destination = FollowController.followDestination(
                new Vector(20.0, 64.0, 0.0), new Vector(0.0, 70.0, 0.0));

        assertEquals(5.0, destination.getX(), 1.0e-9);
        assertEquals(70.0, destination.getY(), 1.0e-9);
    }

    @Test
    void doesNotProduceNaNWhenAtSameHorizontalCoordinates() {
        Vector destination = FollowController.followDestination(
                new Vector(4.0, 64.0, 6.0), new Vector(4.0, 70.0, 6.0));

        assertEquals(4.0, destination.getX(), 1.0e-9);
        assertEquals(70.0, destination.getY(), 1.0e-9);
        assertEquals(6.0, destination.getZ(), 1.0e-9);
    }
}
