package automatons.automatons.utility;

public class NanoTimes {

	public static boolean isBefore(long t1, long t2) {
		return (t1 - t2) <= 0;
	}
	
	public static boolean isBeforeStrict(long t1, long t2) {
		return (t1 - t2) < 0;
	}
	
	public static boolean isBetween(long x, long t1, long t2) {	
		return isBefore(t1, x) && isBefore(x, t2);
	}
	
}
