import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * @author  Geunho Khim
 * @created 11/26/13, 6:41 PM
 * @updated 11/26/13
 */
public class HelperClass {

  public static List<String> getRandFriends(List<String> users, int max) {
    List<String> result = new ArrayList<String>();
    int size = users.size();

    // if the size of friend list is smaller than max, just return friend list
    if(size <= max) {
      return users;

    } else {
      // hash set of random index
      HashSet<Integer> randFriends = new HashSet<Integer>();
      Random rand = new Random();

      while(randFriends.size() != max) {
        int idx = rand.nextInt(size);
        randFriends.add(idx);
      }

      for(Integer idx : randFriends) {
        result.add(users.get(idx));
      }
    }

    return result;
  }
  @Test
  public void testGetRandFriends() {
    int numRand = 5; // change here to test
    List<String> users = new ArrayList<String>();
    users.add("1234"); users.add("1235"); users.add("1236");
    users.add("1237"); users.add("1238"); users.add("1239");
    users.add("1240"); users.add("1241"); users.add("1242");

    long start = System.currentTimeMillis();
    List<String> randFriends = getRandFriends(users, numRand);

    System.out.println("\ntime spent: " + (System.currentTimeMillis() - start) + "ms");
    System.out.println("random users below: " + numRand);

    for(String id : randFriends) {
      System.out.println(id);
    }
  }

}