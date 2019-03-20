package bitbucketpullrequestbuilder.bitbucketpullrequestbuilder;

import bitbucketpullrequestbuilder.bitbucketpullrequestbuilder.bitbucket.AbstractPullrequest;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
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

public class BitbucketPullRequestsBuilder {
  private static final Logger logger = Logger.getLogger(BitbucketBuildTrigger.class.getName());
  private Job<?, ?> job;
  private BitbucketBuildTrigger trigger;
  private BitbucketRepository repository;
  private BitbucketBuilds builds;

  public BitbucketPullRequestsBuilder(Job<?, ?> job, BitbucketBuildTrigger trigger, String username, String password) {
    this.job = Objects.requireNonNull(job);
    this.trigger = Objects.requireNonNull(trigger);
    this.repository = new BitbucketRepository(this, username, password);
    this.builds = new BitbucketBuilds(this.trigger, this.repository);
  }

  void findAndSchedulePullRequests() {
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
    logger.log(Level.FINE, "Started on {}", DateFormat.getDateTimeInstance().format(new Date()));

    SCMTriggerItem scmTriggerItem = Objects.requireNonNull(SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job));
    MercurialSCM scm = (MercurialSCM) scmTriggerItem.getSCMs().iterator().next();
    String initialRevision = scm.getRevision();

    try {
      Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);
      File repository = new File(System.getProperty("java.io.tmpdir"), job.getName());

      String destinationBranchName = pullRequest.getDestination().getBranch().getName();
      logger.log(Level.INFO, "Getting target branch state: {0}", destinationBranchName);
      SCMRevisionState destinationBranchState = getDestinationBranchState(launcher, taskListener, repository, destinationBranchName, scm);

      String sourceBranchName = pullRequest.getSource().getBranch().getName();
      logger.log(Level.INFO, "Now to actual poll for changes in branch {0}", sourceBranchName);
      scm.setRevision(sourceBranchName);
      PollingResult pollingResult = scm.compareRemoteRevisionWith(job, launcher, new FilePath(repository), taskListener, destinationBranchState);

      logger.log(Level.INFO, "Branch changes are {0}", pollingResult.change);

      return pollingResult.hasChanges();
    } catch (Exception e) {
      e.printStackTrace(taskListener.error("Failed to record SCM polling"));
      logger.log(Level.SEVERE, "Failed to record SCM polling", e);
      //If cannot determine changes, build anyway
      return true;
    } finally {
      scm.setRevision(initialRevision);
    }
  }

  private SCMRevisionState getDestinationBranchState(Launcher launcher, TaskListener listener, File repository, String branchName, MercurialSCM scm) throws IOException, InterruptedException {
    scm.setRevision(branchName);
    scm.checkout(job.getLastBuild(), launcher, new FilePath(repository), listener, null, null);
    return scm.calcRevisionsFromBuild(job.getLastBuild(), new FilePath(repository), launcher, listener);
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
