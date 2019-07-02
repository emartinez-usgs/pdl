/*
 * Reliable Indexer Listener Test
 */

package gov.usgs.earthquake.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import junit.framework.Assert;
import org.junit.Test;

import gov.usgs.earthquake.indexer.ProductSummary;
import gov.usgs.earthquake.product.Product;
import gov.usgs.util.Config;
import gov.usgs.earthquake.indexer.ProductIndexQuery;

public class ReliableIndexerListenerTest {

  private TestIndexerListener synchronizeListener = new TestIndexerListener();
  private ArrayList<ProductSummary> products = new ArrayList<ProductSummary>();
  private long currentIndex = 0;
  private ProductIndexQuery lastQuery;
  private Object nextProducts = new Object();
  private Object productProcessed = new Object();
  private boolean waitForProducts = false;

  @Test
  public void synchronizeTest() throws Exception {
    long testIndex = 7;

    //make sure products list is empty
    products.clear();
    //start up synchronized listener
    synchronizeListener = new TestIndexerListener();
    synchronizeListener.startup();

    //in synchronized:
    //  make wait flag true
    //  trigger indexer event
    synchronized (nextProducts) {
      waitForProducts = true;
    }

    //start new indexer event thread (basically queue new event)
    Thread tr = new Thread(new IndexerEventThread());

    //in synchronized:
    //  notify getNextProducts -> returns empty list
    synchronized(nextProducts) {
      nextProducts.notify();
    }
    //because of queued indexerevent, getNextProducts blocks again

    tr.join();

    //sync on process
    //  sync on next
    //    update next product list
    //    notify so it returns
    //  wait on process (until list is processed)
    synchronized (productProcessed) {
      synchronized (nextProducts) {
        ProductSummary product = new ProductSummary();
        product.setIndexId(testIndex);
        products.add(product);
        nextProducts.notify();
      }
      productProcessed.wait();
    }
    
    //confirm last index is the one we handed
    Assert.assertEquals(testIndex,synchronizeListener.getLastIndexId());

    synchronizeListener.shutdown();
    waitForProducts = false;
  }

  @Test
  public void indexTest() throws Exception {
    products.clear();
    long testIndex = 8;

    //start up listener
    synchronizeListener.startup();

    //update product list
    ProductSummary product = new ProductSummary();
    product.setIndexId(testIndex);
    products.add(product);

    //hand in new event, wait until processed
    synchronized (productProcessed) {
      synchronizeListener.onIndexerEvent(new IndexerEvent(new Indexer())); 
      productProcessed.wait();
    }

    //shutdown synchronizelistener
    synchronizeListener.shutdown();

    //start up listener
    synchronizeListener.startup();

    //confirm that it starts where it left off
    Assert.assertEquals(testIndex,synchronizeListener.getLastIndexId());

    synchronizeListener.shutdown();

  }

  @Test
  public void queryTest() throws Exception {
    long testIndex = 9;
    //set up config properties (maybe don't do it this way)
    Properties props = new Properties();
    ProductIndex index = new TestIndex();
    props.setProperty(Indexer.INDEX_CONFIG_PROPERTY,"gov.usgs.earthquake.ReliableIndexerTest.TestIndexer");
    Config config = new Config(props);

    //start new reliablelistener, call config
    ReliableIndexerListener listener = new ReliableIndexerListener();
    listener.configure(config);
    listener.startup();

    //create new product
    products.clear();
    ProductSummary product = new ProductSummary();
    product.setIndexId(testIndex);
    products.add(product);
    
    //notify of new product (with index)
    synchronized (productProcessed) {
      listener.onIndexerEvent(new IndexerEvent(new Indexer())); 
      productProcessed.wait();
    }

    //confirm correct query for product
    Assert.assertEquals(testIndex,lastQuery.getMinProductIndexId().longValue());
  }

  public class TestIndexerListener extends ReliableIndexerListener {
    //Grabs up to 10 products from product list
    @Override
    public List<ProductSummary> getNextProducts() throws InterruptedException{
      synchronized (nextProducts) {
        if (waitForProducts) {
          nextProducts.wait();
        }
      }
      return products;
    }

    @Override
    public void processProduct(ProductSummary product) throws InterruptedException {
      synchronized (productProcessed) {
        currentIndex = product.getIndexId();
        setLastIndexId(product.getIndexId());
        productProcessed.notify();
      }
    }

    //Updates the index on start like the extent should
    @Override
    public void onBeforeProcessThreadStart() {
      setLastIndexId(currentIndex);
    }

    //Escapes the state so we can confirm
    public Thread.State getState() {
      return this.getState();
    }

  }

  public class IndexerEventThread implements Runnable {

    @Override
    public void run(){
      try {
        synchronizeListener.onIndexerEvent(new IndexerEvent(new Indexer()));
      } catch (Exception e) {
        //Exception happened
      }
    }
    
  }

  public class TestIndex extends JDBCProductIndex {

    public TestIndex() throws Exception {
      super();
    }

    @Override
    public List<ProductSummary> getProducts(ProductIndexQuery query) {
      lastQuery = query;
      return products;
    }
    
  }
}