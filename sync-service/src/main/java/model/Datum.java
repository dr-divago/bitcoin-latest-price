package model;
import java.util.Date;
import java.util.List;

public class Datum {
  public int id;
  public String name;
  public String symbol;
  public String slug;
  public int num_market_pairs;
  public Date date_added;
  public List<String> tags;
  public int max_supply;
  public int circulating_supply;
  public int total_supply;
  public Object platform;
  public int cmc_rank;
  public Date last_updated;
  public Quote quote;
}
