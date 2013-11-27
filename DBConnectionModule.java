import org.apache.cassandra.thrift.*;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.thrift.TException;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author  Geunho Khim
 * @created 10/11/13, 6:43 PM
 * @updated 11/27/13
 *
 *  test module to test Cassandra I/O
 */
public class DBConnectionModule {
  private static DBConnectionModule instance = new DBConnectionModule();
  private static Connector thriftConnector = new Connector();

  public static DBConnectionModule getInstance() {
    return instance;
  }

  // Connection method. (port / hostname / keyspace) are depend on the environment.
  public static Connection getConnection() throws Exception {
    return getConnection("165.246.44.92", "sop_db_1", "9160");
  }

  public static Connection getConnection(String _hostname, String _keyspace, String _port) throws Exception {
    Connection conn;
    final String port = _port;
    final String hostname = _hostname;
    final String keyspace = _keyspace;

    Class.forName("org.apache.cassandra.cql.jdbc.CassandraDriver");
    conn = DriverManager.getConnection("jdbc:cassandra://" + hostname +":"+ port +"/"+ keyspace);

    return conn;
  }

  /**
   *  Sticky cf 에 새로 추가한 user_name 컬럼 값을 채우기 위한 테스트 함수
   */
  @Test
  public void updateUserName() throws Exception {
    List<String> urls = getURLs(100, getConnection());

    for(String url : urls) {
      List<Sticky> stickies = getAllStickies(url, getConnection());

      for(Sticky sticky : stickies) {
        String user_id = sticky.getUserID();
        String user_name = "";

        if(user_id.equals("Idontknow") || user_id.equals("geunho.khim@gmail.com"))
          user_name = "근호";
        if(user_id.equals("naheon.kim"))
          user_name = "나헌";
        if(user_id.equals("100001840897145") || user_id.equals("F100001840897145") || user_id.equals("K.SW.Engineer"))
          user_name = "성원";

        updateSticky(sticky.getURL(), user_id, user_name, sticky.getTimestamp().getTime(), getConnection());
      }

    }
  }

  /**
   *
   * @param url, userID, sticky, connection
   * @throws SQLException
   *
   *  Insert sticky memo to the "Sticky" CF. (by CQL)
   *    CF: Sticky
   *      Key: url,userID,created {
   *       (column_name: "userID", value: userID)
   *       (column_name: "sticky", value: sticky)
   *       (column_name: "created", value: timestamp)
   *      }
   *
   *  1. Insert Sticky
   *  2. Update(Insert) URL --> sticky_counter++
   *
   */
  public void writeSticky(String url, String userID, String sticky, String user_name, Connection conn) throws SQLException {
    Statement stmt = null;

    try {
      stmt = conn.createStatement();
      long ts = System.currentTimeMillis();

      String query = "insert into \"Sticky\"(url, user_id, sticky, created, like, user_name) values"
              +"('"+ url +"','"+ userID +"','"+ sticky +"',"+ ts +","+ 0 +",'"+ user_name +"');";
      stmt.executeUpdate(query);

      // additional update methods
      addURL(url, 0, 1, conn); // update url sticky count
      updateUptodate(userID, user_name, ts, url, sticky, conn); // update user's latest sticky
      addUrlToUser(userID, url); // add user's url list

    }  catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();
  }
  @Test
  public void testWriteSticky() throws Exception {
    Connection conn = getConnection();
    writeSticky("http://www.datastax.com/docs/1.1/references/cql/cql_data_types",
            "geunho.khim@gmail.com", "Four more test.", "geunho", conn);
  }

  /**
   *
   * @param url, userID, created, sticky(updated content), conn
   * @throws SQLException
   *
   *   update sticky's content
   *  스티키를 텝 했을때 Sticky 객체로부터 url, userID, timestamp 들을 가져온다. 이 세 항목이 하나의 스티키를 나타내는 composite key 이다.
   *  writeSticky 메소드와 다른점은 addURL 메소드를 호출하지 않는다는 점이다. (URL의 스티키 카운트는 변동 없으므로)
   *  또한 timestamp(created) 는 composite key의 일부이므로 업데이트 될 수 없다.
   *
   *  --> 스티키 내용의 갱신 기능은 생략할 예정. 따라서 해당 메소드는 null 값의 user_name 컬럼을 갱신하는데 이용한다.
   */
  @Deprecated
  public void updateSticky(String url, String userID, String user_name, long created, Connection conn) throws SQLException {
    Statement stmt = null;

    try {
      stmt = conn.createStatement();

      String query = "update \"Sticky\" set  user_name = '" + user_name + "'" +
              " where url = '" + url + "' and user_id = '" + userID + "' and created = " + created +";";

      stmt.executeUpdate(query);
    } catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();
  }

  /**
   *
   * @param url, user, connection
   * @return stickies of specific user
   * @throws SQLException
   *
   *  url의 특정 유저의 스티키들을 가져온다.
   */
  public List<Sticky> getStickies(String url, String user, Connection conn) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;

    try {
      stmt = conn.createStatement();
      String query = "select user_id, user_name, created, like, sticky from \"Sticky\" where url = '" + url + "' and user_id = '" + user +"';";
      rs = stmt.executeQuery(query);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    List<Sticky> stickies = new ArrayList<Sticky>();

    while(rs.next()) {
      Sticky sticky = new Sticky();
      sticky.setURL(url);
      sticky.setUser(rs.getString(1));
      sticky.setUserName(rs.getString(2));
      sticky.setTimestamp(rs.getDate(3));
      sticky.setLike(rs.getInt(4));
      sticky.setMemo(rs.getString(5));
      stickies.add(sticky);
    }

    rs.close();
    stmt.close();

    return stickies;
  }
  @Test
  public void testGetStickies() throws Exception {
    Connection conn = getConnection();
    List<Sticky> stickies = getStickies("http://wsnews.co.kr/society/index_view.php?zipEncode===am1udoX0tB152x3vwA2zImX0tB15KmLrxyJzsn90wDoftz0f2yMetpSfMvWLME", "geunho.khim@gmail.com", conn);
    System.out.println("(url, user_id, created, like, sticky)");
    for(Sticky sticky : stickies) {
      System.out.println(sticky.getURL() +", "+ sticky.getUserID() +", "+ sticky.getTimestamp() +", "+ sticky.getLike() +", "+ sticky.getMemo());
    }
  }

  /**
   *
   * @param url, conn
   * @return all stickies of url
   * @throws SQLException
   *
   *  url의 모든 스티키를 가져온다.
   */
  public List<Sticky> getAllStickies(String url, Connection conn) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;

    try {
      stmt = conn.createStatement();
      String query = "select user_id, user_name, created, like, sticky from \"Sticky\" where url = '" + url + "';";
      rs = stmt.executeQuery(query);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    List<Sticky> stickies = new ArrayList<Sticky>();

    while(rs.next()) {
      Sticky sticky = new Sticky();
      sticky.setURL(url);
      sticky.setUser(rs.getString(1));
      sticky.setUserName(rs.getString(2));
      sticky.setTimestamp(rs.getDate(3));
      sticky.setLike(rs.getInt(4));
      sticky.setMemo(rs.getString(5));
      stickies.add(sticky);
    }

    rs.close();
    stmt.close();

    return stickies;
  }
  @Test
  public void testGetAllStickies() throws Exception {
    Connection conn = getConnection();
    List<Sticky> stickies = getAllStickies("http://en.wikipedia.org/wiki/Tf%E2%80%93idf", conn);
    System.out.println("(url, user_id, created, like, sticky)");
    for(Sticky sticky : stickies) {
      System.out.println(sticky.getURL() +", "+ sticky.getUserID() +", "+ sticky.getTimestamp() +", "+ sticky.getLike() +", "+ sticky.getMemo());
    }
  }

  /**
   *
   * @param url
   * @param conn
   *
   *  Insert url address to the "URL" CF. (by CQL)
   *    CF: URL
   *      RowKey: url {
   *        (column name: "url", value: url)
   *        (column name: "extract_count", value: counter)
   *        (column name: "sticky_count", value: counter)
   *      }
   *  1. insertion of counter CF is done by update query, not insert.
   *  2. count의 업데이트
   */
  public void addURL(String url, int e_count, int s_count, Connection conn) throws SQLException {
    url = url.replace("'", "%27"); // apostrophe 를 %27로 모두 치환한다.
    Statement stmt = null;

    try {
      stmt = conn.createStatement();

      String query = "update \"URL\" set extract_count = extract_count +"+ e_count
              +", sticky_count = sticky_count + "+ s_count +" where KEY = '" + url + "';";
      stmt.executeUpdate(query);
    } catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();
  }
  @Test
  /**
   *  This test program execute inserting 5,716,808 url address of Wikipedia(en) to the URL CF.
   */
  public void testAddURL() throws Exception {
    long startTime = System.currentTimeMillis();

    String currentDir = System.getProperty("user.dir");
    FileReader fr = new FileReader(currentDir + "/resource/wiki-titles-sorted.txt");
    BufferedReader br = new BufferedReader(fr, 500); // buffer size is 500

    Connection conn = getConnection();
    String title;
    int count = 0;
    for(int i = 0; i < 1000; i++) {
      title = br.readLine();
      addURL("http://en.wikipedia.org/wiki/" + title, 0, 0, conn);
      count++;
      if(count%1000==0)
        System.out.print("1");
    }

    long endTime = System.currentTimeMillis();
    System.out.println("\nInserting " + count + " urls, took " + (endTime - startTime) + " milliseconds");
  }

  /**
   *
   * @param  user_id, f_id, url, connection
   * @throws SQLException
   *
   *  Add preference to specific sticky, column like++ of Sticky CF.
   *    CF: Preference
   *      RowKey: user_id
   *        (column name: f_id:url:created, value: null)
   *
   *    첫 primary key가 유저 아이디이므로 한 유저의 모든 선호도를 바로 가져올 수 있는 장점이 있다.
   *
   */
  public void addPreference(String user_id, String f_id, String url, long created, Connection conn) throws SQLException {
    Statement stmt = null;

    try {
      stmt = conn.createStatement();
      countLike(url, user_id, created, stmt, conn);

      long ts = System.currentTimeMillis();
      String query = "insert into \"Preference\"(user_id, f_id, url, created) values('" + user_id +"','"+ f_id + "','" + url + "','" + ts + "');";
      stmt.executeUpdate(query);

    } catch (SQLException e) {
      e.printStackTrace();
    }

    stmt.close();
  }

  private void countLike(String url, String userID, long created, Statement stmt, Connection conn) throws SQLException {

    String query = "select like from \"Sticky\" where url = '" + url + "' and user_id = '" + userID + "' and created = " + created + ";";
    int likeCount = stmt.executeQuery(query).getInt(1);
    String updateQuery = "update \"Sticky\" set like = " + (likeCount + 1) +
            " where url = '" + url + "' and user_id = '" + userID + "' and created = " + created + ";";
    stmt.executeUpdate(updateQuery);
  }

  /**
   *
   * @param   limit, connection
   * @return  list of URL
   *
   * @throws SQLException
   *
   *  get URL list limit (for test)
   */
  public List<String> getURLs(int limit, Connection conn) throws SQLException {
    List<String> urls = new ArrayList<String>();
    Statement stmt = null;
    ResultSet rs = null;

    try {
      stmt = conn.createStatement();
      String query = "select key from \"URL\" limit " + limit;
      stmt.executeQuery(query);
      rs = stmt.getResultSet();

    } catch (SQLException e) {
      e.printStackTrace();
    }

    while(rs.next()) {
      urls.add(rs.getString(1));
    }

    rs.close();
    stmt.close();

    return urls;
  }
  @Test
  public void testGetURLs() throws Exception {
    int limit = 10;
    Connection conn = getConnection();
    List<String> urls = getURLs(limit, conn);

    System.out.println(urls.toString());

  }

  /**
   *
   * @param   url
   * @return  similar url list
   *
   *  get most similar urls of target url.
   *
   * CF: Recommendation
   *  RowKey: target url
   *    (column name: similar url, value: similarity)
   *
   *   반환 타입이 Map으로, 유사도를 key 값으로 오름차순 정렬되어 있다. TreeMap으로 casting 하여 반환한다면
   *   pollLastEntry() 메소드로 큰 값부터 가져올 수 있다.
   *
   */
  public Map<Double, String> getRecommendation(String url)
          throws TException, InvalidRequestException, UnavailableException, TimedOutException, CharacterCodingException {
    TreeMap<Double, String> recommendList = new TreeMap<Double, String>();
    Cassandra.Client client = thriftConnector.connect();
    ByteBuffer key = ByteBufferUtil.bytes(url);

    SlicePredicate predicate = new SlicePredicate();
    SliceRange sliceRange = new SliceRange();
    sliceRange.setStart(new byte[0]);
    sliceRange.setFinish(new byte[0]);
    predicate.setSlice_range(sliceRange);

    String columnFamily = "Recommendation";
    ColumnParent parent = new ColumnParent(columnFamily);

    List<ColumnOrSuperColumn> cols = client.get_slice(key, parent, predicate, ConsistencyLevel.ONE);

    for (ColumnOrSuperColumn cosc : cols) {
      Column column = cosc.column;
      String recommend = ByteBufferUtil.string(column.name);
      Double similarity = ByteBufferUtil.toDouble(column.value);

      recommendList.put(similarity, recommend);
    }

    return recommendList;
  }
  @Test
  public void testGetRecommendation()
          throws UnavailableException, TException, InvalidRequestException, TimedOutException, CharacterCodingException {
    String url = "http://en.wikipedia.org/wiki/$1_Money_Wars";
    Map<Double, String> recommends = getRecommendation(url);

    System.out.println(recommends.toString());
  }

  /**
   *
   * @param   user_id, user_name, conn
   * @throws  SQLException
   *
   *  insert user info when logging in the SOP. before insertion, check if user exists.
   */
  public void insertUser(String user_id, String user_name, Connection conn) throws SQLException {
    Statement stmt = null;

    if(!isUserExist(user_id, conn)) {
      try {
        stmt = conn.createStatement();
        String query = "insert into \"User\"(key, user_name, sticky_count) values ('" + user_id + "', '" + user_name + "', " + 0 + ");";
        stmt.executeUpdate(query);

      } catch (Exception e) {
        e.printStackTrace();
      }

      stmt.close();
    }
  }
  @Test
  public void testInsertUser() throws Exception {
    insertUser("geunho.khim@gmail.com", "geunhokim", getConnection());
  }

  /**
   *
   * @param userID, url
   * @throws TException
   * @throws InvalidRequestException
   * @throws UnsupportedEncodingException
   * @throws UnavailableException
   * @throws TimedOutException
   */
  public void addUrlToUser(String userID, String url)
          throws TException, InvalidRequestException, UnsupportedEncodingException, UnavailableException, TimedOutException {
    Cassandra.Client client = thriftConnector.connect();
    String columnFamily = "User";
    ColumnParent columnParent = new ColumnParent(columnFamily);

    Column column = new Column();
    column.setName(ByteBufferUtil.bytes(url));
    column.setValue(new byte[0]);
    column.setTimestamp(System.currentTimeMillis());

    client.insert(ByteBufferUtil.bytes(userID), columnParent, column, ConsistencyLevel.ONE);
  }
  @Test
  public void TestAddUrlToUser() throws UnavailableException, TException, InvalidRequestException, TimedOutException, UnsupportedEncodingException {
    addUrlToUser("geunho.khim@gmail.com", "http://gmarket.co.kr");
  }

  /**
   *
   * @param   user_id, conn
   * @return  bool
   * @throws  SQLException
   *
   *  check if user info already exists before insert user cf
   */
  public boolean isUserExist(String user_id, Connection conn) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;

    try {
    stmt = conn.createStatement();
    String query = "select count(*) from \"User\" where key = '" + user_id + "';";
    stmt.executeQuery(query);
    rs = stmt.getResultSet();

    if(rs.getInt(1) == 1) {
      return true;
    }

  } catch (Exception e) {
    e.printStackTrace();
  }

  rs.close();
  stmt.close();

  return false;
  }
  @Test
  public void testIsUserExist() throws Exception {
    boolean isExist = isUserExist("geunho.khim@gmail.com", getConnection());
    System.out.println(isExist);
  }

  /**
   *
   * @param   user_id, created, url, content, conn
   * @throws  SQLException
   *
   *  update uptodate column in User cf. used in writeSticky method
   * uptodate 컬럼의 데이터는 created::url::content 의 형식으로 저장된다. (초기 화면에서 이용할 때 '::' 로 스트링을 파싱한다.
   */
  public void updateUptodate(String user_id, String user_name, long created, String url, String content, Connection conn) throws SQLException {
    Statement stmt = null;

    try {
      stmt = conn.createStatement();
      String query = "update \"User\" set sticky_count = " +(getUserStickyCount(user_id, conn) + 1)+ ", uptodate = '" + created + "::" + url + "::" + user_name + "::" + content + "' where key = '" + user_id + "';";
      stmt.executeUpdate(query);

    } catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();
  }

  /**
   *
   * @param   userID, conn
   * @return  user's sticky count
   * @throws SQLException
   *
   *  used in updateUptodate method
   */
  public int getUserStickyCount(String userID, Connection conn) throws SQLException {
    Statement stmt = null;
    int sticky_count = 0;

    try {
      stmt = conn.createStatement();
      String query = "select sticky_count from \"User\" where key = '" + userID + "';";
      sticky_count = stmt.executeQuery(query).getInt(1);

    } catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();

    return sticky_count;
  }
  @Test
  public void testGetUserStickyCount() throws Exception {
    String userID = "geunho.khim@gmail.com";
    int count = getUserStickyCount(userID, getConnection());
    System.out.println(userID + "'s sticky count is: " + count);
  }

  /**
   *
   * @param   userID, conn
   * @return  latest sticky
   * @throws  SQLException
   *
   *  get latest sticky of User cf. SOP 의 처음 페이지에서 사용된다.
   * TODO: 속도가 느리므로 처음 로딩 화면에서 전부 불러온 후 화면을 구성하는 방법을 이용한다. --> 한정된 랜덤수의 친구를 선별해 가져오기
   */
  public Sticky getLatestSticky(String userID, Connection conn) throws SQLException {
    Statement stmt = null;
    Sticky sticky = null;
    String uptodate = null;

    try {
      stmt = conn.createStatement();
      String query = "select uptodate from \"User\" where key = '" + userID + "';";
      uptodate = stmt.executeQuery(query).getString(1);
      sticky = parseUptodate(uptodate);
      sticky.setUser(userID);

    } catch (Exception e) {
      e.printStackTrace();
    }

    stmt.close();

    return sticky;
  }

  private Sticky parseUptodate(String uptodate) {
    Sticky sticky = new Sticky();
    String[] parsed = uptodate.split("::");

    sticky.setTimestamp(new Date(Long.parseLong(parsed[0])));
    sticky.setURL(parsed[1]);
    sticky.setUser(parsed[2]);
    sticky.setMemo(parsed[3]);

    return sticky;
  }
  @Test
  public void testGetLatestSticky() throws Exception {
    long start = System.currentTimeMillis();
    Sticky sticky = getLatestSticky("geunho.khim@gmail.com", getConnection());

    System.out.println("time spent: " + (float)(System.currentTimeMillis() - start) / 1000 + "s");
    System.out.println(sticky.toString());
  }

  /**
   *
   * @param   url, conn
   * @return  sticky count of url
   * @throws  SQLException
   *
   *  get sticky_count column from URL cf
   */
  public int getURLStickyCount(String url, Connection conn) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    int count = 0;

    try {
      stmt = conn.createStatement();
      String query = "select sticky_count from \"URL\" where key = '" + url + "';";
      rs = stmt.executeQuery(query);
      count = rs.getInt(1);

    } catch (Exception e) {
      e.printStackTrace();
    }

    rs.close();
    stmt.close();

    return count;
  }
  @Test
  public void testGetURLStickyCount() throws Exception {
    String url = "http://m.daum.net/";
    int count = getURLStickyCount(url, getConnection());
    System.out.println(url + "\n sticky count: " + count);
  }

}
