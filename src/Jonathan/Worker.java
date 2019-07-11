package Jonathan;

import aic2019.*;

public class Worker {
    UnitController uc;

    final int INF = 1000000;
    int counter = 0;
    int resetCounter = 10;
    Location target_location;

    // Variables de exploración
    int rpointer = 0;
    int epointer = 1000;
    int apointer = 2000;

    // Variables de pathfinding
    boolean rotateLeftRight = false, rotateNorthSouth = false;
    boolean rotateRight = true; //if I should rotate right or left
    Location lastObstacleFound = null; //latest obstacle I've found in my way
    int minDistToEnemy = INF; //minimum distance I've been to the enemy while going around an obstacle
    Location prevTarget = null; //previous target

    public Worker(UnitController uc) {
        this.uc = uc;
    }

    public void run () {
        target_location = new Location(uc.getLocation().x + resetCounter, uc.getLocation().y +resetCounter);

        while (true){
            moverTrabajador();
            observar();
            uc.yield(); //End of turn
        }
    }

    private void moverTrabajador() {

        moveTo(target_location);
    }


    // REGION EXPLORACION
    private void observar() {
        ResourceInfo[] recursos = uc.senseResources();
        UnitInfo[] aliados = uc.senseUnits(uc.getTeam(),false);
        UnitInfo[] enemigos = uc.senseUnits(uc.getTeam(), true);

        for (ResourceInfo r : recursos) {
            if (r.getResource() == Resource.WOOD) {
                addResource(1, r.getLocation());
            }
            else if (r.getResource() == Resource.IRON) {
                addResource(2, r.getLocation());
            }
            else if (r.getResource() == Resource.CRYSTAL) {
                addResource(3, r.getLocation());
            }
        }
        for (UnitInfo a : aliados) {
            addAlly(a.getID(), a.getLocation());
        }
        for (UnitInfo e : enemigos) {
            addEnemy(e.getID(), e.getLocation());
        }
    }

    private void addResource(int type, Location loc) {
        int mem = rpointer;
        boolean exists = false;

        while (uc.read(mem) != 0) {
            if (uc.read(mem+1) == loc.x && uc.read(mem+2) == loc.y) {
                exists = true;
                break;
            }
            mem+=3;
            rpointer+=3;
        }

        if (!exists) {
            uc.write(mem, type);
            uc.write(mem+1, loc.x);
            uc.write(mem+2, loc.y);
        }
    }

    private void addEnemy(int id, Location loc) {
        int mem = epointer;
        boolean exists = false;

        while (uc.read(mem) != 0) {
            if (uc.read(mem+1) == id) {
                exists = true;
                break;
            }
            mem+=3;
            epointer+=3;
        }

        if (!exists)
            uc.write(200001,uc.read(200001)+1);

        uc.write(mem, id);
        uc.write(mem+1, loc.x);
        uc.write(mem+2, loc.y);
    }

    private void addAlly(int id, Location loc) {
        int mem = apointer;

        while (uc.read(mem) != 0) {
            if (uc.read(mem+1) == id) {
                break;
            }
            mem+=3;
            apointer+=3;
        }

        uc.write(mem, id);
        uc.write(mem+1, loc.x);
        uc.write(mem+2, loc.y);
    }

    void moveTo(Location target){
        //No target? ==> bye!
        if (target == null) return;

        //different target? ==> previous data does not help!
        if (prevTarget == null || !target.isEqual(prevTarget)) resetPathfinding();

        //If I'm at a minimum distance to the target, I'm free!
        Location myLoc = uc.getLocation();
        int d = myLoc.distanceSquared(target);
        if (d <= minDistToEnemy) resetPathfinding();

        //Update data
        prevTarget = target;
        minDistToEnemy = Math.min(d, minDistToEnemy);

        //If there's an obstacle I try to go around it [until I'm free] instead of going to the target directly
        Direction dir = myLoc.directionTo(target);
        if (lastObstacleFound != null) dir = myLoc.directionTo(lastObstacleFound);

        //This should not happen for a single unit, but whatever
        if (uc.canMove(dir)) resetPathfinding();

        //I rotate clockwise or counterclockwise (depends on 'rotateRight'). If I try to go out of the map I change the orientation
        //Note that we have to try at most 16 times since we can switch orientation in the middle of the loop. (It can be done more efficiently)
        for (int i = 0; i < 16; ++i){
            if (uc.canMove(dir)){
                uc.move(dir);
                return;
            }
            else {
                counter = resetCounter;
                rotateLeftRight = !rotateLeftRight;
                rotateNorthSouth = !rotateNorthSouth;
            }
            Location newLoc = myLoc.add(dir);
            if (uc.isOutOfMap(newLoc)) rotateRight = !rotateRight;
                //If I could not go in that direction and it was not outside of the map, then this is the latest obstacle found
            else lastObstacleFound = myLoc.add(dir);
            if (rotateRight) dir = dir.rotateRight();
            else dir = dir.rotateLeft();
        }

        if (uc.canMove(dir)) uc.move(dir);
    }

    //clear some of the previous data
    void resetPathfinding(){
        lastObstacleFound = null;
        minDistToEnemy = INF;
    }
}
