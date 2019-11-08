/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMailboxAssert.assertMailboxes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAssert;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.Test;

/**
 * Generic purpose tests for your implementation MailboxMapper.
 * 
 * You then just need to instantiate your mailbox mapper and an IdGenerator.
 */
public abstract class MailboxMapperTest {
    
    private static final char DELIMITER = '.';
    private static final char WILDCARD = '%';
    private static final long UID_VALIDITY = 42;

    private MailboxPath benwaInboxPath;
    private Mailbox benwaInboxMailbox;
    private MailboxPath benwaWorkPath;
    private Mailbox benwaWorkMailbox;
    private MailboxPath benwaWorkTodoPath;
    private Mailbox benwaWorkTodoMailbox;
    private MailboxPath benwaPersoPath;
    private Mailbox benwaPersoMailbox;
    private MailboxPath benwaWorkDonePath;
    private Mailbox benwaWorkDoneMailbox;
    private MailboxPath bobInboxPath;
    private Mailbox bobyMailbox;
    private MailboxPath bobyMailboxPath;
    private Mailbox bobInboxMailbox;
    private MailboxPath bobDifferentNamespacePath;
    private Mailbox bobDifferentNamespaceMailbox;

    private MailboxMapper mailboxMapper;

    protected abstract MailboxMapper createMailboxMapper();

    protected abstract MailboxId generateId();

    public void setUp() throws Exception {
        this.mailboxMapper = createMailboxMapper();
        
        initData();
    }

    @Test
    public void findMailboxByPathWhenAbsentShouldFail() throws MailboxException {
        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(MailboxPath.forUser("benwa", "INBOX")))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void saveShouldPersistTheMailbox() throws MailboxException {
        mailboxMapper.save(benwaInboxMailbox);
        MailboxAssert.assertThat(mailboxMapper.findMailboxByPath(benwaInboxPath)).isEqualTo(benwaInboxMailbox);
    }

    @Test
    public void saveShouldThrowWhenMailboxAlreadyExist() throws MailboxException {
        mailboxMapper.save(benwaInboxMailbox);

        Mailbox mailbox = new Mailbox(benwaInboxMailbox);
        mailbox.setMailboxId(null);

        assertThatThrownBy(() -> mailboxMapper.save(mailbox))
            .isInstanceOf(MailboxExistsException.class);
    }

    @Test
    public void listShouldRetrieveAllMailbox() throws MailboxException {
        saveAll();
        List<Mailbox> mailboxes = mailboxMapper.list();

        assertMailboxes(mailboxes)
            .containOnly(benwaInboxMailbox, benwaWorkMailbox, benwaWorkTodoMailbox, benwaPersoMailbox, benwaWorkDoneMailbox, 
                bobyMailbox, bobDifferentNamespaceMailbox, bobInboxMailbox);
    }
    
    @Test
    public void hasChildrenShouldReturnFalseWhenNoChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(benwaWorkTodoMailbox, DELIMITER)).isFalse();
    }

    @Test
    public void hasChildrenShouldReturnTrueWhenChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(benwaInboxMailbox, DELIMITER)).isTrue();
    }

    @Test
    public void hasChildrenShouldNotBeAcrossUsersAndNamespace() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(bobInboxMailbox, '.')).isFalse();
    }

    @Test
    public void findMailboxWithPathLikeShouldBeLimitedToUserAndNamespace() throws MailboxException {
        saveAll();
        MailboxPath mailboxPathQuery = new MailboxPath(bobInboxMailbox.getNamespace(), bobInboxMailbox.getUser(), "IN" + WILDCARD);
        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxPathQuery);

        assertMailboxes(mailboxes).containOnly(bobInboxMailbox);
    }
    
    @Test
    public void deleteShouldEraseTheGivenMailbox() throws MailboxException {
        saveAll();
        mailboxMapper.delete(benwaInboxMailbox);

        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(benwaInboxPath))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxWithPathLikeWithChildRegexShouldRetrieveChildren() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(benwaWorkPath.getNamespace(), benwaWorkPath.getUser(), benwaWorkPath.getName() + WILDCARD);
        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(regexPath);

        assertMailboxes(mailboxes).containOnly(benwaWorkMailbox, benwaWorkDoneMailbox, benwaWorkTodoMailbox);
    }

    @Test
    public void findMailboxWithPathLikeWithRegexShouldRetrieveCorrespondingMailbox() throws MailboxException {
        saveAll();
        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(benwaWorkPath)
            .expression(new ExactName("INBOX"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxQuery);

        assertMailboxes(mailboxes).containOnly(benwaInboxMailbox);
    }

    @Test
    public void findMailboxWithPathLikeShouldEscapeMailboxName() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(benwaInboxPath.getNamespace(), benwaInboxPath.getUser(), "INB?X");
        assertThat(mailboxMapper.findMailboxWithPathLike(regexPath)).isEmpty();
    }

    @Test
    public void findMailboxByIdShouldReturnExistingMailbox() throws MailboxException {
        saveAll();
        Mailbox actual = mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId());
        MailboxAssert.assertThat(actual).isEqualTo(benwaInboxMailbox);
    }
    
    @Test
    public void findMailboxByIdShouldFailWhenAbsent() throws MailboxException {
        saveAll();
        MailboxId removed = benwaInboxMailbox.getMailboxId();
        mailboxMapper.delete(benwaInboxMailbox);
        assertThatThrownBy(() -> mailboxMapper.findMailboxById(removed))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    private void initData() {
        benwaInboxPath = MailboxPath.forUser("benwa", "INBOX");
        benwaWorkPath = MailboxPath.forUser("benwa", "INBOX" + DELIMITER + "work");
        benwaWorkTodoPath = MailboxPath.forUser("benwa", "INBOX" + DELIMITER + "work" + DELIMITER + "todo");
        benwaPersoPath = MailboxPath.forUser("benwa", "INBOX" + DELIMITER + "perso");
        benwaWorkDonePath = MailboxPath.forUser("benwa", "INBOX" + DELIMITER + "work" + DELIMITER + "done");
        bobInboxPath = MailboxPath.forUser("bob", "INBOX");
        bobyMailboxPath = MailboxPath.forUser("boby", "INBOX.that.is.a.trick");
        bobDifferentNamespacePath = new MailboxPath("#private_bob", "bob", "INBOX.bob");

        benwaInboxMailbox = createMailbox(benwaInboxPath);
        benwaWorkMailbox = createMailbox(benwaWorkPath);
        benwaWorkTodoMailbox = createMailbox(benwaWorkTodoPath);
        benwaPersoMailbox = createMailbox(benwaPersoPath);
        benwaWorkDoneMailbox = createMailbox(benwaWorkDonePath);
        bobInboxMailbox = createMailbox(bobInboxPath);
        bobyMailbox = createMailbox(bobyMailboxPath);
        bobDifferentNamespaceMailbox = createMailbox(bobDifferentNamespacePath);
    }

    private void saveAll() throws MailboxException {
        mailboxMapper.save(benwaInboxMailbox);
        mailboxMapper.save(benwaWorkMailbox);
        mailboxMapper.save(benwaWorkTodoMailbox);
        mailboxMapper.save(benwaPersoMailbox);
        mailboxMapper.save(benwaWorkDoneMailbox);
        mailboxMapper.save(bobyMailbox);
        mailboxMapper.save(bobDifferentNamespaceMailbox);
        mailboxMapper.save(bobInboxMailbox);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) {
        Mailbox mailbox = new Mailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(generateId());
        return mailbox;
    }

}
