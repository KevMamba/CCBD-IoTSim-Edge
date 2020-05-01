package org.edge.core.feature;

public class Mobility {

	public boolean movable;


	public double volecity;

	public double totalMovingDistance;

	public MovingRange range;
	public double signalRange;

	public Location location;

	public Mobility(Location location) {
		super();
		this.location=new Location(location.x,location.y,location.z);

	}


	public static class MovingRange{
		public int beginX;
		public int endX;
		public int beginY;
		public int endY;
		public int beginZ;
		public int endZ;

		public MovingRange() {
			super();
		}

		public MovingRange(int beginX, int endX, int beginY, int endY, int beginZ, int endZ) {
			super();
			this.beginX = beginX;
			this.endX = endX;
			this.beginY =  beginY;
			this.endY = endY;
			this.beginZ =  beginZ;
			this.endZ = endZ;

		}

		public MovingRange(int beginX, int endX) {
			super();
			this.beginX = beginX;
			this.endX = endX;
		}
	}


	public static class Location{
		public double x;
		public double y;
		public double z;

		public Location(double x,double y,double z) {
			this.x = x;
			this.y = y;
			this.z = z;

		}


	}


}
