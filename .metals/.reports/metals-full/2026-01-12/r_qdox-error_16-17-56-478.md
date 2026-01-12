error id: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/business/gdspecialprice/poster/GDSpecialPriceFilterSupplier.java
file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/business/gdspecialprice/poster/GDSpecialPriceFilterSupplier.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[68,1]

error in qdox parser
file content:
```java
offset: 2652
uri: file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/business/gdspecialprice/poster/GDSpecialPriceFilterSupplier.java
text:
```scala
package com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.poster;

import com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.GDSpecialPriceBusiness;
import com.wuxiansheng.shieldarch.marsdata.business.gdspecialprice.ReasonSupplierResult;
import com.wuxiansheng.shieldarch.marsdata.config.LLMConfigHelper;
import com.wuxiansheng.shieldarch.marsdata.llm.Business;
import com.wuxiansheng.shieldarch.marsdata.llm.BusinessContext;
import com.wuxiansheng.shieldarch.marsdata.llm.Poster;
import com.wuxiansheng.shieldarch.marsdata.monitor.StatsdClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按白名单过滤供应商
 */
@Slf4j
@Component
public class GDSpecialPriceFilterSupplier implements Poster {
    
    @Autowired
    private LLMConfigHelper llmConfigHelper;
    
    @Autowired(required = false)
    private StatsdClient statsdClient;
    
    @Override
    public Business apply(BusinessContext bctx, Business business) {
        if (!(business instanceof GDSpecialPriceBusiness)) {
            log.warn("FilterSupplier: invalid business type: {}", business != null ? business.getClass() : "null");
            return business;
        }
        
        GDSpecialPriceBusiness gb = (GDSpecialPriceBusiness) business;
        
        if (gb.getReasonResult() == null || gb.getReasonResult().getSuppliersInfo() == null) {
            return gb;
        }
        
        List<ReasonSupplierResult> validSuppliers = new ArrayList<>();
        String businessName = business.getName();
        
        for (ReasonSupplierResult supplierInfo : gb.getReasonResult().getSuppliersInfo()) {
            if (appConfigService instanceof ApolloConfigService) {
                ApolloConfigService apolloConfigService = (ApolloConfigService) appConfigService;
                if (apolloConfigService.isValidGDSupplier(supplierInfo.getSupplier())) {
                    validSuppliers.add(supplierInfo);
                } else {
                // 上报被过滤的供应商
                if (statsdClient != null) {
                    statsdClient.incrementCounter("filtered_partner", 
                        Map.of("business", businessName, "partner", supplierInfo.getSupplier()));
                }
                log.info("FilterValidPartner filter partner name: {}, business_name: {}", 
                    supplierInfo.getSupplier(), businessName);
            }
        }
        
        gb.getReasonResult().setSuppliersInfo(validSuppliers);
        return gb;
    }
}

@@
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:677)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:674)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:674)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:912)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/com/wuxiansheng/shieldarch/marsdata/business/gdspecialprice/poster/GDSpecialPriceFilterSupplier.java