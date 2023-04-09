package model;

import java.util.Date;
import java.util.List;

public class Datum {
    public int id;
    public String name;
    public String symbol;
    public String slug;
    public int cmc_rank;
    public int num_market_pairs;
    public int circulating_supply;
    public int total_supply;
    public int  market_cap_by_total_supply;
    public int max_supply;
    public boolean infinite_supply;
    public Date last_updated;
    public Date date_added;
    public List<String> tags;
    public int self_reported_circulating_supply;
    public int self_reported_market_cap;
    public int tvl_ratio;
    public Object platform;
    public Quote quote;
}
