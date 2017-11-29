package eric.myapplication.Misc;


import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static eric.myapplication.Database.TravelContract.TravelEntry.PT_COST;
import static eric.myapplication.Database.TravelContract.TravelEntry.PT_TIME;
import static eric.myapplication.Database.TravelContract.TravelEntry.TAXI_COST;
import static eric.myapplication.Database.TravelContract.TravelEntry.TAXI_TIME;
import static eric.myapplication.Database.TravelContract.TravelEntry.WALK_TIME;
import static eric.myapplication.Database.TravelDBHelper.getEntry;

public class TSPFastSolver {
    private String originName;
    private SQLiteDatabase travelDB;
    private List<Route> allRoutes = new ArrayList<>();

    public TSPFastSolver(SQLiteDatabase sqLiteDatabase) {
        this.travelDB = sqLiteDatabase;
    }

    public Route findBestRoute(String originName, List<String> placesToVisit, int budget) {
        List<String> tempRoute = new ArrayList<>();
        this.originName = originName;
        tempRoute.add(originName);

        travelDB.beginTransaction();
        findAllRoutes(tempRoute, placesToVisit);

        Collections.sort(allRoutes);
        Route bestRoute = allRoutes.get(0);
        bestRoute.setPaths(initPaths(bestRoute.getPlaces()));
        if (bestRoute.getCostWeight() <= budget) {
            return bestRoute;
        }

        // Best route with all TAXI did not meet the budget
        // Replace transport modes on paths that are the least efficient
        List<Path> replacedPaths = bestRoute.getPaths();
        List<Path> sortedPaths = new ArrayList<>(replacedPaths);
        Collections.sort(sortedPaths);

        int index = 0;
        while (bestRoute.getCostWeight() > budget) {
            Path sPath = sortedPaths.get(index);
            if (sPath.getTimeIncreasePerCostSaving() > 0) {
                for (Path oPath : replacedPaths)
                    if (sPath.equals(oPath)) {
                        oPath.setToAltTransportMode();

                        double timeIncrease = sPath.getAltTime() - sPath.getTaxiTime();
                        double costDecrease = sPath.getTaxiCost() - sPath.getAltCost();
                        bestRoute.addTimeWeight(timeIncrease);
                        bestRoute.reduceCostWeight(costDecrease);
                    }
            }

            index++;
        }

        bestRoute.setPaths(replacedPaths);
        travelDB.setTransactionSuccessful();
        travelDB.endTransaction();

        return bestRoute;
    }

    private void findAllRoutes(List<String> tempRoute, List<String> unvisitedAttractions) {
        if (unvisitedAttractions.isEmpty()) {
            tempRoute.add(originName);
            double totalTime = routeWeight(TAXI_TIME, tempRoute);
            double totalCost = routeWeight(TAXI_COST, tempRoute);
            Route route = new Route(tempRoute, totalTime, totalCost);
            allRoutes.add(route);
            return;
        }

        String currentAttr = tempRoute.get(tempRoute.size() - 1);
        String nearestAttr = null;
        double leastTime = 0;

        for (String attrName : unvisitedAttractions) {
            if (nearestAttr == null) {
                nearestAttr = attrName;
                leastTime = getEntry(travelDB, TAXI_TIME, currentAttr, nearestAttr);
            }

            double currentTime = getEntry(travelDB, TAXI_TIME, currentAttr, attrName);
            if (currentTime < leastTime) {
                nearestAttr = attrName;
                leastTime = currentTime;
            }
        }

        tempRoute.add(nearestAttr);
        unvisitedAttractions.remove(nearestAttr);
        findAllRoutes(tempRoute, unvisitedAttractions);
    }

    private double routeWeight(String tableName, List<String> route) {
        double routeWeight = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            String from = route.get(i);
            String to = route.get(i + 1);
            routeWeight += getEntry(travelDB, tableName, from, to);
        }

        return routeWeight;
    }

    private List<Path> initPaths(List<String> places) {
        List<Path> paths = new ArrayList<>();

        for (int i = 0; i < places.size() - 1; i++) {
            String from = places.get(i);
            String to = places.get(i + 1);

            // Get time and cost for all transport modes
            double taxiTime = getEntry(travelDB, TAXI_TIME, from, to);
            double taxiCost = getEntry(travelDB, TAXI_COST, from, to);
            double busTime = getEntry(travelDB, PT_TIME, from, to);
            double busCost = getEntry(travelDB, PT_COST, from, to);
            double walkTime = getEntry(travelDB, WALK_TIME, from, to);

            double busTimeCost = (busTime - taxiTime) / (taxiCost - busCost);
            double walkTimeCost = (walkTime - taxiTime) / taxiCost;

            String altTransportMode = "BUS";
            double altTime = busTime;
            double altCost = busCost;
            double timeIncreasePerCostSaving = busTimeCost;

            // Get most efficient alternative transport mode
            if (busTimeCost < 0 || busTimeCost > walkTimeCost) {
                altTransportMode = "WALK";
                altTime = walkTime;
                altCost = 0;
                timeIncreasePerCostSaving = walkTimeCost;
            }

            // Add path to list of paths
            Path path = new Path(from, to, taxiTime, taxiCost, altTime, altCost,
                    altTransportMode, timeIncreasePerCostSaving);
            paths.add(path);
        }

        return paths;
    }
}