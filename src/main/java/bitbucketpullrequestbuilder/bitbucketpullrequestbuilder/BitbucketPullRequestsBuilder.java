package bitbucketpullrequestbuilder.bitbucketpullrequestbuilder;

import bitbucketpullrequestbuilder.bitbucketpullrequestbuilder.bitbucket.AbstractPullrequest;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.PollingResult;
import hudson.util.LogTaskListener;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.codec.binary.Hex;

/**
 * Created by nishio
 */
public class BitbucketPullRequestsBuilder {
  private static final Logger logger = Logger.getLogger(BitbucketBuildTrigger.class.getName());
  private Job<?, ?> job;
  private BitbucketBuildTrigger trigger;
  private BitbucketRepository repository;
  private BitbucketBuilds builds;

  public static BitbucketPullRequestsBuilder getBuilder() {
    return new BitbucketPullRequestsBuilder();
  }

  public void stop() {
    // TODO?
  }

  public void run() {
    this.repository.init();
    LogTaskListener taskListener = new LogTaskListener(logger, Level.INFO);

    List<AbstractPullrequest> targetPullRequests = new ArrayList<>();
    for (AbstractPullrequest pullRequest : this.repository.getTargetPullRequests()) {
      if (isRelevant(pullRequest, taskListener)) {
        targetPullRequests.add(pullRequest);
      }
    }

    this.repository.addFutureBuildTasks(targetPullRequests);
  }

  /*
    Checks if given pull request changed files that this job is interested in.
    Mostly to filter out changes outside of the specified modules.
  */
  private boolean isRelevant(AbstractPullrequest pullRequest, TaskListener taskListener) {
    try {
      logger.log(Level.FINE, "Started on {}", DateFormat.getDateTimeInstance().format(new Date()));

      SCMTriggerItem scmTriggerItem = Objects.requireNonNull(SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job));
      MercurialSCM scm = (MercurialSCM) scmTriggerItem.getSCMs().iterator().next();
      String initialRevision = scm.getRevision();

      String destinationBranchName = pullRequest.getDestination().getBranch().getName();
      logger.log(Level.INFO, "Resetting poll baseline to target branch: {0}", destinationBranchName);
      scm.setRevision(destinationBranchName);
      scmTriggerItem.poll(taskListener);

      String sourceBranchName = pullRequest.getSource().getBranch().getName();
      logger.log(Level.INFO, "Now to actual poll for changes in branch {0}", sourceBranchName);
      scm.setRevision(sourceBranchName);
      PollingResult pollingResult = scmTriggerItem.poll(taskListener);

      logger.log(Level.INFO, "Branch changes are {0}", pollingResult.change);
      scm.setRevision(initialRevision);

      return pollingResult.hasChanges();
    } catch (Error | RuntimeException e) {
      e.printStackTrace(taskListener.error("Failed to record SCM polling"));
      logger.log(Level.SEVERE, "Failed to record SCM polling", e);
      //If cannot determine changes, build anyway
      return true;
    }
  }

  public BitbucketPullRequestsBuilder setupBuilder() {
    if (this.job == null || this.trigger == null) {
      throw new IllegalStateException();
    }
    this.repository = new BitbucketRepository(this.trigger.getProjectPath(), this);
    this.repository.init();
    this.builds = new BitbucketBuilds(this.trigger, this.repository);
    return this;
  }

  public void setJob(Job<?, ?> job) {
    this.job = job;
  }

  public void setTrigger(BitbucketBuildTrigger trigger) {
    this.trigger = trigger;
  }

  public Job<?, ?> getJob() {
    return this.job;
  }

  /**
   * Return MD5 hashed full project name or full project name, if MD5 hash provider inaccessible
   *
   * @return unique project id
   */
  public String getProjectId() {
    try {
      final MessageDigest MD5 = MessageDigest.getInstance("MD5");
      return new String(Hex.encodeHex(MD5.digest(this.job.getFullName().getBytes(StandardCharsets.UTF_8))));
    } catch (NoSuchAlgorithmException exc) {
      logger.log(Level.WARNING, "Failed to produce hash", exc);
    }
    return this.job.getFullName();

  }

  public BitbucketBuildTrigger getTrigger() {
    return this.trigger;
  }

  public BitbucketBuilds getBuilds() {
    return this.builds;
  }
}
