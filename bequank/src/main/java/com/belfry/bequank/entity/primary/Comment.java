package com.belfry.bequank.entity.primary;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Yang Yuqing
 * @Description:
 * @Date: Created in 5:21 PM 8/17/18
 * @Modifiedby:
 */
@Entity
@Getter
@Setter
public class Comment implements Serializable {
    @Id
    @GeneratedValue
    private Long id;
    private Long writerid;         // 这个也准备换成User对象
    private String content,time,nickname;
    private boolean alreadyLiked;

    @ManyToOne(targetEntity = Tutorial.class, cascade = {CascadeType.MERGE},fetch = FetchType.EAGER)
    private Tutorial tutorial;

    @ManyToOne(targetEntity = Comment.class, cascade = {CascadeType.MERGE})
    private Comment replyTarget;
    @OneToMany(targetEntity = Comment.class, cascade = {CascadeType.MERGE,CascadeType.REMOVE},mappedBy = "replyTarget")
    private List<Comment> comments;
    private ArrayList<Long> likedusers;
    public Comment(){
        this.likedusers=new ArrayList<>();
        this.comments=new ArrayList<>();
    }
    public Comment(Long writerid, String content, String time, String nickname, int likecount, Tutorial tutorial, Comment replyTarget) {
        this.writerid = writerid;
        this.content = content;
        this.time = time;
        this.nickname = nickname;
        this.tutorial = tutorial;
        this.replyTarget = replyTarget;
        this.comments = new ArrayList<>();
        this.likedusers=new ArrayList<>();
    }
}

