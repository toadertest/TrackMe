package com.ant.lib.db.tables;

import com.ant.lib.db.TrackMeContract;
import com.ant.lib.db.columns.GenericColumn;
import com.ant.lib.db.columns.GenericColumnImpl;
import com.ant.lib.db.type.SqliteDataType;

/**
 * Database columns.
 */
public enum DatabaseTable {

    ROUTE(new GenericColumnImpl(true, true, TrackMeContract.RouteEntry._ID, SqliteDataType.String),
            new GenericColumnImpl(false, TrackMeContract.RouteEntry.TOTAL_TIME, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.NAME, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RouteEntry.DESCRIPTION, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RouteEntry.START_POINT_ID, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteEntry.STOP_POINT_ID, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteEntry.START_TIME, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.STOP_TIME, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.AVG_SPEED, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.MAX_SPEED, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.TOTAL_DISTANCE, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.MIN_LONGITUDE, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteEntry.MAX_LONGITUDE, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteEntry.NUM_ROUTE_POINTS, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteEntry.MAX_ELEVATION, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteEntry.MIN_ELEVATION, SqliteDataType.Float)
    ),

    ROUTE_POINT(new GenericColumnImpl(true, TrackMeContract.RoutePointEntry._ID, SqliteDataType.String),
            new GenericColumnImpl(false, false, true, TrackMeContract.RoutePointEntry.ROUTE_ID, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.DESCRIPTION, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.NAME, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.TIME, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.SPEED, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.LOCATION_ALT, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.LOCATION_ACCURACY, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.ROUTE_ACTIVITY_MODE, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.LOCATION_LAT, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.LOCATION_LONG, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RoutePointEntry.LOCATION_BEARING, SqliteDataType.Float)
    ),

    ROUTE_MAP_POINT(new GenericColumnImpl(true, TrackMeContract.RouteCheckPointEntry._ID, SqliteDataType.String),
            new GenericColumnImpl(false, true, TrackMeContract.RouteCheckPointEntry.ROUTE_ID, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.DESCRIPTION, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.NAME, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.TIME, SqliteDataType.Integer),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.SPEED, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.LOCATION_ALT, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.LOCATION_ACCURACY, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.ROUTE_ACTIVITY_MODE, SqliteDataType.String),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.LOCATION_LAT, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.LOCATION_LONG, SqliteDataType.Float),
            new GenericColumnImpl(TrackMeContract.RouteCheckPointEntry.LOCATION_BEARING, SqliteDataType.Float));

    private GenericColumn[] genericColumns;

    private DatabaseTable(GenericColumn... columns) {
        this.genericColumns = columns;
    }

    public GenericColumn[] getColumns() {
        return genericColumns;
    }

    public String getName() {
        return this.name();
    }
}
