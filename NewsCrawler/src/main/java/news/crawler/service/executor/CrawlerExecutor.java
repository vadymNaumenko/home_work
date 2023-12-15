package news.crawler.service.executor;

import lombok.extern.slf4j.Slf4j;
import news.crawler.controller.dto.EventDTO;
import news.crawler.domain.SourceConfig;
import news.crawler.repository.SourceConfigRepository;
import news.crawler.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.util.List;

@Slf4j
@Service
public class CrawlerExecutor implements SmartLifecycle {


    @Value("${executor.enabled:true}")
    private boolean serviceEnabled;

    @Value("${executor.waitMin:60}")
    private int waitMin;

    @Autowired
    private SourceConfigRepository sourceConfigRepository;

    @Autowired
    private EventService eventService;

    private enum ThreadStatus {
        RUNNING, STOP_REQUEST, STOPPED
    }

    public static final String PACKAGE = "news.crawler.service.executor.";

    private ThreadStatus status = ThreadStatus.STOPPED;
    private final Object lock = new Object();

    public void run() {
        synchronized (lock) {
            while (status == ThreadStatus.RUNNING) {

                List<SourceConfig> configs = sourceConfigRepository.findAll();
                for (SourceConfig config : configs) {
                    if (config.getDisabled() == null || !config.getDisabled()) {
                        try {
                            // create parser object by name of class using reflection
                            Class<?> cls = Class.forName(PACKAGE + config.getClassName());
                            Constructor<?> constructor = cls.getConstructor();
                            Execute execClass = (Execute) constructor.newInstance();

                            // read titles of news from web
                            log.info("Reading from {}{}...", config.getRootUrl(), config.getNewsSuffix());
                            List<EventDTO> events = execClass.readUrl(config);
                            log.info("Read {} titles from {}.", events.size(), config.getRootUrl());

                            // because of the filtering, only new news remains
                            events = eventService.filterEvents(events);
                            log.info("Define {} news as new.", events.size());

                            // read new news from web
                            events = execClass.readNews(events);
                            eventService.save(events, config);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    lock.wait(1000 * 60 * waitMin);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                    break;
                }
            }
            status = ThreadStatus.STOPPED;
            lock.notifyAll();
        }
    }

    @Override
    public void start() {
        log.info("Service starting...");
        if (!serviceEnabled) {
            log.info("Service is disabled.");
            status = ThreadStatus.STOPPED;
            return;
        }
        status = ThreadStatus.RUNNING;
        new Thread(() -> {
            run();
        }).start();
        log.info("Service is started.");
    }

    @Override
    public void stop() {
        log.info("Service stopping...");
        status = ThreadStatus.STOP_REQUEST;
        synchronized (lock) {
            lock.notifyAll();
            while (status != ThreadStatus.STOPPED) {
                try {
                    lock.wait(100);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                    break;
                }
            }
        }
        log.info("Service is stopped.");
    }

    @Override
    public boolean isRunning() {
        return status == ThreadStatus.RUNNING;
    }
}
